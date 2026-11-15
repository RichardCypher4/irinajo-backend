package com.irinajo.sync;

import com.irinajo.common.ApiException;
import com.irinajo.common.ChangeSequence;
import com.irinajo.common.UserLock;
import com.irinajo.explore.SafetyFeedbackService;
import com.irinajo.geo.LatLng;
import com.irinajo.geo.Polyline;
import com.irinajo.sync.SyncDtos.*;
import com.irinajo.user.Profile;
import com.irinajo.user.ProfileService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Backup and restore.
 *  - Upload is idempotent: journeys are keyed by the phone's id, and every edit carries updatedAt.
 *  - Last write wins by updatedAt; a delete is final and beats any older edit.
 *  - A delete for a journey we've never seen is stored as a tombstone.
 *  - Keepsakes are add-only.
 */
@Service
public class SyncService {

    private final JourneyRepository journeys;
    private final KeepsakeRepository keepsakes;
    private final ProfileService profiles;
    private final ChangeSequence seq;
    private final UserLock lock;
    private final SafetyFeedbackService safety;

    public SyncService(JourneyRepository journeys, KeepsakeRepository keepsakes, ProfileService profiles,
                       ChangeSequence seq, UserLock lock, SafetyFeedbackService safety) {
        this.journeys = journeys;
        this.keepsakes = keepsakes;
        this.profiles = profiles;
        this.seq = seq;
        this.lock = lock;
        this.safety = safety;
    }

    @Transactional
    public UploadResult upload(UUID userId, UploadBatch batch) {
        lock.forWrite(userId);
        Profile profile = profiles.getOrCreate(userId);
        boolean shareForSafety = profile.isShareAnonymisedWalks();

        List<ItemResult> results = new ArrayList<>();
        if (batch.journeys() != null) {
            for (JourneyDto dto : batch.journeys()) {
                results.add(new ItemResult(dto.id(), apply(userId, dto, shareForSafety)));
            }
        }
        int added = 0;
        if (batch.keepsakes() != null) {
            for (KeepsakeDto k : batch.keepsakes()) {
                added += keepsakes.addOnly(userId, k.id(), k.earnedAt(), seq.next());
            }
        }
        return new UploadResult(results, added);
    }

    private String apply(UUID userId, JourneyDto dto, boolean shareForSafety) {
        Optional<Journey> found = journeys.findById(new Journey.Key(userId, dto.id()));

        if (found.isPresent()) {
            Journey j = found.get();
            if (j.isDeleted()) return dto.deleted() ? "duplicate" : "stale";        // delete is final
            if (dto.updatedAt() < j.getUpdatedAt()) return "stale";                   // older edit loses
            if (dto.updatedAt() == j.getUpdatedAt() && !dto.deleted()) return "duplicate"; // retry of same change
        }

        Journey j = found.orElseGet(() -> new Journey(userId, dto.id()));

        if (dto.deleted()) {
            j.markDeleted(dto.updatedAt());
            j.setSeq(seq.next());
            journeys.save(j);
            return "applied";
        }

        if (found.isEmpty()) validateNew(dto);

        Boolean oldFeltSafe = j.getFeltSafe();
        String oldPolyline = j.getPolyline();

        // Journeys are created on the phone; route/stats fields are set once and edits only touch
        // customName and feltSafe, but we accept a full row so a re-upload is always harmless.
        if (dto.startedAt() != null) j.setStartedAt(dto.startedAt());
        if (dto.durationSec() != null) j.setDurationSec(Math.max(0, dto.durationSec()));
        if (dto.distanceKm() != null) j.setDistanceKm(dto.distanceKm().setScale(2, RoundingMode.HALF_UP));
        if (dto.steps() != null) j.setSteps(Math.max(0, dto.steps()));
        if (dto.timeOfDay() != null) j.setTimeOfDay(dto.timeOfDay());
        if (dto.kmTimes() != null) j.setKmTimes(dto.kmTimes());
        if (dto.polyline() != null && !dto.polyline().equals(oldPolyline)) {
            j.setPolyline(dto.polyline());
            List<LatLng> pts = Polyline.decode(dto.polyline());
            if (!pts.isEmpty()) {
                j.setStartLat(pts.get(0).lat());
                j.setStartLng(pts.get(0).lng());
                j.setGeocoded(false);  // the geocoder fills in area + landmarks, then bumps seq
            } else {
                j.setGeocoded(true);
            }
        }
        j.setCustomName(blankToNull(dto.customName()));
        j.setFeltSafe(dto.feltSafe());
        j.setUpdatedAt(dto.updatedAt());
        j.setSeq(seq.next());
        journeys.save(j);

        if (shareForSafety && j.getStartedAt() != null && !Objects.equals(oldFeltSafe, dto.feltSafe())) {
            safety.record(j.getPolyline(), j.getStartedAt(), oldFeltSafe, dto.feltSafe());
        }
        return "applied";
    }

    private static void validateNew(JourneyDto dto) {
        if (dto.startedAt() == null || dto.durationSec() == null || dto.distanceKm() == null) {
            throw ApiException.badRequest("JOURNEY_INCOMPLETE", "Journey " + dto.id() + " is missing startedAt, durationSec or distanceKm");
        }
        if (dto.distanceKm().compareTo(BigDecimal.ZERO) < 0 || dto.distanceKm().compareTo(BigDecimal.valueOf(300)) > 0) {
            throw ApiException.badRequest("JOURNEY_DISTANCE_INVALID", "distanceKm out of range");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Changes since a cursor. scope=recent returns the profile, keepsakes and journeys started in the
     * last 90 days (plus tombstones) so a new phone is usable quickly; then the app pulls scope=all
     * from cursor 0 in the background (duplicates are harmless).
     */
    @Transactional // not readOnly: Postgres refuses SELECT ... FOR SHARE in read-only transactions
    public Changes changes(UUID userId, long since, int limit, String scope) {
        lock.forRead(userId);
        int max = Math.min(Math.max(limit, 1), 500);
        Profile p = profiles.find(userId).orElse(null);
        var profile = p != null && p.getSeq() > since ? profiles.toDto(p) : null;

        List<Keepsake> keepsakeRows = keepsakes.changesSince(userId, since);
        List<KeepsakeDto> ks = keepsakeRows.stream()
                .map(k -> new KeepsakeDto(k.getKeepsakeId(), k.getEarnedAt())).toList();

        List<Journey> js;
        if ("recent".equalsIgnoreCase(scope)) {
            long cutoff = System.currentTimeMillis() - Duration.ofDays(90).toMillis();
            js = journeys.recentChangesSince(userId, since, cutoff, PageRequest.of(0, max + 1));
        } else {
            js = journeys.changesSince(userId, since, PageRequest.of(0, max + 1));
        }
        boolean hasMore = js.size() > max;
        if (hasMore) js = js.subList(0, max);

        long cursor = since;
        for (Journey j : js) cursor = Math.max(cursor, j.getSeq());
        // Only advance past profile/keepsake seqs when journeys are complete, so nothing is skipped.
        if (!hasMore) {
            if (p != null) cursor = Math.max(cursor, p.getSeq());
            for (Keepsake k : keepsakeRows) cursor = Math.max(cursor, k.getSeq());
        }
        return new Changes(profile, ks, js.stream().map(JourneyDto::of).toList(), cursor, hasMore);
    }
}
