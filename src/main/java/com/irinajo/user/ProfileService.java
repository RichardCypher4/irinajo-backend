package com.irinajo.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.irinajo.common.ApiException;
import com.irinajo.common.ChangeSequence;
import com.irinajo.common.UserLock;
import com.irinajo.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileService {
    private static final Set<String> LANGS = Set.of("en", "pcm");
    private static final Set<String> MOTIVATIONS = Set.of("active", "explore", "challenge", "justWalk");
    private static final Set<String> REGIONS = Set.of("south", "north");
    private static final Set<String> SPEAKERS = Set.of("ada", "tunde");
    private static final Set<String> MUSIC = Set.of("duck", "pause", "mix");

    private final ProfileRepository profiles;
    private final UserRepository users;
    private final ChangeSequence seq;
    private final StorageService storage;
    private final UserLock userLock;

    public ProfileService(ProfileRepository profiles, UserRepository users, ChangeSequence seq,
                          StorageService storage, UserLock userLock) {
        this.profiles = profiles;
        this.users = users;
        this.seq = seq;
        this.storage = storage;
        this.userLock = userLock;
    }

    @Transactional
    public Profile getOrCreate(UUID userId) {
        return profiles.findById(userId).orElseGet(() -> {
            Profile p = new Profile(userId);
            p.setSeq(seq.next());
            return profiles.save(p);
        });
    }

    public Profile getOrCreateForExport(UUID userId) {
        return profiles.findById(userId).orElseGet(() -> new Profile(userId));
    }

    public java.util.Optional<Profile> find(UUID userId) {
        return profiles.findById(userId);
    }

    public ProfileDto toDto(Profile p) {
        User u = users.findById(p.getUserId()).orElseThrow();
        return new ProfileDto(p.getName(), p.getBio(), p.getCity(), p.getLanguage(), p.getDailyGoalKm(),
                p.getWeightKg(), p.getWeightGoalKgPerWeek(), p.getMotivations(), p.getRegion(),
                p.getVoiceSettings(), p.getReminderSettings(), p.isShareAnonymisedWalks(),
                p.isWalkSafeAutoAfterDark(), storage.publicUrl(p.getAvatarKey()), storage.publicUrl(p.getCoverKey()),
                u.getMemberSince().toEpochMilli(), p.getUpdatedAt());
    }

    public record PatchResult(ProfileDto profile, boolean applied) {}

    /**
     * Partial update. Only fields present in the JSON are changed; an explicit null clears a nullable field.
     * Last write wins by the phone's updatedAt (epoch ms).
     */
    @Transactional
    public PatchResult patch(UUID userId, JsonNode body) {
        if (body == null || !body.has("updatedAt")) throw ApiException.badRequest("UPDATED_AT_REQUIRED", "updatedAt is required");
        long updatedAt = body.get("updatedAt").asLong();
        userLock.forWrite(userId);
        Profile p = getOrCreate(userId);
        if (updatedAt < p.getUpdatedAt()) return new PatchResult(toDto(p), false); // older edit loses

        if (body.has("name")) p.setName(text(body, "name", 30));
        if (body.has("bio")) p.setBio(text(body, "bio", 160));
        if (body.has("city")) p.setCity(text(body, "city", 80));
        if (body.has("language")) p.setLanguage(oneOf(body, "language", LANGS));
        if (body.has("region")) p.setRegion(oneOf(body, "region", REGIONS));

        if (body.has("dailyGoalKm")) {
            BigDecimal g = body.get("dailyGoalKm").decimalValue();
            boolean halfStep = g.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() <= 0;
            if (g.compareTo(new BigDecimal("1.0")) < 0 || g.compareTo(new BigDecimal("15.0")) > 0 || !halfStep) {
                throw ApiException.badRequest("DAILY_GOAL_INVALID", "dailyGoalKm must be 1.0 to 15.0 in 0.5 steps");
            }
            p.setDailyGoalKm(g);
        }
        if (body.has("weightKg")) {
            JsonNode n = body.get("weightKg");
            if (n.isNull()) p.setWeightKg(null);
            else {
                BigDecimal w = n.decimalValue();
                if (w.compareTo(BigDecimal.valueOf(20)) < 0 || w.compareTo(BigDecimal.valueOf(400)) > 0)
                    throw ApiException.badRequest("WEIGHT_INVALID", "weightKg out of range");
                p.setWeightKg(w);
            }
        }
        if (body.has("weightGoalKgPerWeek")) {
            JsonNode n = body.get("weightGoalKgPerWeek");
            if (n.isNull()) p.setWeightGoalKgPerWeek(null);
            else {
                BigDecimal w = n.decimalValue();
                if (w.abs().compareTo(new BigDecimal("0.5")) > 0)
                    throw ApiException.badRequest("WEIGHT_GOAL_INVALID", "Weight goal is capped at 0.5 kg a week");
                p.setWeightGoalKgPerWeek(w);
            }
        }
        if (body.has("motivations")) {
            List<String> m = new ArrayList<>();
            body.get("motivations").forEach(x -> {
                if (!MOTIVATIONS.contains(x.asText())) throw ApiException.badRequest("MOTIVATION_INVALID", "Unknown motivation");
                if (!m.contains(x.asText())) m.add(x.asText());
            });
            p.setMotivations(m);
        }
        if (body.has("voice")) {
            JsonNode v = body.get("voice");
            var cur = p.getVoiceSettings() == null ? Profile.VoiceSettings.defaults() : p.getVoiceSettings();
            String speaker = v.has("speaker") ? oneOf(v, "speaker", SPEAKERS) : cur.speaker();
            String music = v.has("music") ? oneOf(v, "music", MUSIC) : cur.music();
            p.setVoiceSettings(new Profile.VoiceSettings(
                    v.has("announcements") ? v.get("announcements").asBoolean() : cur.announcements(),
                    speaker,
                    v.has("calories") ? v.get("calories").asBoolean() : cur.calories(),
                    music));
        }
        if (body.has("reminders")) {
            JsonNode r = body.get("reminders");
            var cur = p.getReminderSettings() == null ? Profile.ReminderSettings.defaults() : p.getReminderSettings();
            Integer minute = cur.minute();
            if (r.has("minute")) {
                if (r.get("minute").isNull()) minute = null;
                else {
                    minute = r.get("minute").asInt();
                    if (minute < 0 || minute > 1439) throw ApiException.badRequest("REMINDER_MINUTE_INVALID", "minute must be 0-1439 or null");
                }
            }
            p.setReminderSettings(new Profile.ReminderSettings(
                    r.has("daily") ? r.get("daily").asBoolean() : cur.daily(),
                    minute,
                    r.has("streak") ? r.get("streak").asBoolean() : cur.streak(),
                    r.has("weekly") ? r.get("weekly").asBoolean() : cur.weekly()));
        }
        if (body.has("shareAnonymisedWalks")) p.setShareAnonymisedWalks(body.get("shareAnonymisedWalks").asBoolean());
        if (body.has("walkSafeAutoAfterDark")) p.setWalkSafeAutoAfterDark(body.get("walkSafeAutoAfterDark").asBoolean());

        if (body.has("avatarKey")) p.setAvatarKey(photoKey(userId, body, "avatarKey", p.getAvatarKey()));
        if (body.has("coverKey")) p.setCoverKey(photoKey(userId, body, "coverKey", p.getCoverKey()));

        p.setUpdatedAt(updatedAt);
        p.setSeq(seq.next());
        return new PatchResult(toDto(profiles.save(p)), true);
    }

    /** Only accept keys we issued for this user; delete the photo being replaced. */
    private String photoKey(UUID userId, JsonNode body, String field, String old) {
        JsonNode n = body.get(field);
        String key = n.isNull() ? null : n.asText();
        if (key != null && !key.startsWith("users/" + userId + "/")) {
            throw ApiException.forbidden("PHOTO_KEY_INVALID", "Unknown photo");
        }
        if (old != null && !old.equals(key)) storage.delete(old);
        return key;
    }

    private static String text(JsonNode body, String field, int max) {
        JsonNode n = body.get(field);
        if (n.isNull()) return null;
        String s = n.asText().trim();
        if (s.codePointCount(0, s.length()) > max) throw ApiException.badRequest(field.toUpperCase() + "_TOO_LONG", field + " is limited to " + max + " characters");
        return s.isEmpty() ? null : s;
    }

    private static String oneOf(JsonNode body, String field, Set<String> allowed) {
        String s = body.get(field).asText();
        if (!allowed.contains(s)) throw ApiException.badRequest(field.toUpperCase() + "_INVALID", field + " must be one of " + allowed);
        return s;
    }
}
