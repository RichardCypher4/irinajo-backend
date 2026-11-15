package com.irinajo.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.irinajo.common.ApiException;
import com.irinajo.common.Mask;
import com.irinajo.geo.LatLng;
import com.irinajo.geo.Polyline;
import com.irinajo.storage.StorageService;
import com.irinajo.sync.Journey;
import com.irinajo.sync.JourneyRepository;
import com.irinajo.sync.KeepsakeRepository;
import com.irinajo.user.ProfileService;
import com.irinajo.user.User;
import com.irinajo.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** "Download my data" and "Delete account" (App Store, Google Play and NDPA 2023 requirements). */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final ProfileService profiles;
    private final JourneyRepository journeys;
    private final KeepsakeRepository keepsakes;
    private final StorageService storage;
    private final ObjectMapper mapper;

    public AccountService(UserRepository users, ProfileService profiles, JourneyRepository journeys,
                          KeepsakeRepository keepsakes, StorageService storage, ObjectMapper mapper) {
        this.users = users;
        this.profiles = profiles;
        this.journeys = journeys;
        this.keepsakes = keepsakes;
        this.storage = storage;
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** ZIP containing profile.json, journeys.json, keepsakes.json and one GPX file per walk. */
    @Transactional(readOnly = true)
    public byte[] export(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found"));
        var profile = profiles.toDto(profiles.getOrCreateForExport(userId));
        List<Journey> walks = journeys.allActive(userId);

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bos)) {
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("id", u.getId());
            account.put("phone", u.getPhone());
            account.put("email", u.getEmail());
            account.put("googleLinked", u.getGoogleSub() != null);
            account.put("appleLinked", u.getAppleSub() != null);
            account.put("memberSince", u.getMemberSince().toString());
            account.put("profile", profile);
            put(zip, "profile.json", mapper.writeValueAsBytes(account));

            put(zip, "journeys.json", mapper.writeValueAsBytes(walks.stream().map(j -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", j.getId());
                m.put("startedAt", j.getStartedAt() == null ? null : Instant.ofEpochMilli(j.getStartedAt()).toString());
                m.put("durationSec", j.getDurationSec());
                m.put("distanceKm", j.getDistanceKm());
                m.put("steps", j.getSteps());
                m.put("timeOfDay", j.getTimeOfDay());
                m.put("area", j.getArea());
                m.put("landmarks", j.getLandmarks());
                m.put("customName", j.getCustomName());
                m.put("kmTimes", j.getKmTimes());
                m.put("feltSafe", j.getFeltSafe());
                m.put("gpxFile", "gpx/" + j.getId() + ".gpx");
                return m;
            }).toList()));

            put(zip, "keepsakes.json", mapper.writeValueAsBytes(keepsakes.findByUserId(userId).stream()
                    .map(k -> Map.of("id", k.getKeepsakeId(), "earnedAt", Instant.ofEpochMilli(k.getEarnedAt()).toString()))
                    .toList()));

            for (Journey j : walks) {
                if (j.getPolyline() == null) continue;
                put(zip, "gpx/" + j.getId() + ".gpx", gpx(j).getBytes(StandardCharsets.UTF_8));
            }
            zip.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Export failed", e);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String gpx(Journey j) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<gpx version=\"1.1\" creator=\"Irin Ajo\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
          .append("  <trk><name>").append(xml(j.getCustomName() != null ? j.getCustomName() : "Walk " + j.getId()))
          .append("</name><trkseg>\n");
        for (LatLng p : Polyline.decode(j.getPolyline())) {
            sb.append("    <trkpt lat=\"").append(p.lat()).append("\" lon=\"").append(p.lng()).append("\"/>\n");
        }
        sb.append("  </trkseg></trk>\n</gpx>\n");
        return sb.toString();
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Deletes the account and everything tied to it. Child tables (profile, journeys, keepsakes,
     * contacts, sessions, points, tokens) go via ON DELETE CASCADE; photos are removed from storage.
     * Anonymous safety_feedback counts hold no user id and are kept.
     */
    @Transactional
    public void delete(UUID userId) {
        users.findById(userId).ifPresent(u -> {
            storage.deletePrefix("users/" + userId + "/");
            users.delete(u);
            log.info("Account deleted ({})", Mask.phone(u.getPhone()));
        });
    }
}
