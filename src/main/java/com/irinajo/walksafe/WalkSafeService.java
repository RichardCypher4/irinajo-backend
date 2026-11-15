package com.irinajo.walksafe;

import com.irinajo.common.ApiException;
import com.irinajo.common.Phones;
import com.irinajo.common.Tokens;
import com.irinajo.config.AppProperties;
import com.irinajo.geo.Geo;
import com.irinajo.geo.LatLng;
import com.irinajo.push.PushService;
import com.irinajo.user.ProfileService;
import com.irinajo.user.User;
import com.irinajo.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WalkSafeService {
    public static final int MAX_CONTACTS = 3;

    public record ContactDto(UUID id, String name, String phone, boolean isDefault, String link) {}
    public record SessionDto(UUID id, String status, long startedAt, Long endedAt, double distanceKm) {}
    public record Point(double lat, double lng, long t) {}
    public record PointsResult(int accepted, double distanceKm) {}

    private final ContactRepository contacts;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final ProfileService profiles;
    private final PushService push;
    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public WalkSafeService(ContactRepository contacts, SessionRepository sessions, UserRepository users,
                           ProfileService profiles, PushService push, JdbcTemplate jdbc, AppProperties props) {
        this.contacts = contacts;
        this.sessions = sessions;
        this.users = users;
        this.profiles = profiles;
        this.push = push;
        this.jdbc = jdbc;
        this.props = props;
    }

    // ---------------- Contacts ----------------

    public List<ContactDto> list(UUID userId) {
        return contacts.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::dto).toList();
    }

    @Transactional
    public ContactDto add(UUID userId, String name, String rawPhone, boolean makeDefault) {
        String phone = Phones.normalise(rawPhone);
        List<Contact> existing = contacts.findByUserIdOrderByCreatedAtAsc(userId);
        // Idempotent: adding the same number again returns the existing contact.
        for (Contact c : existing) if (c.getPhone().equals(phone)) return dto(c);
        if (existing.size() >= MAX_CONTACTS) throw ApiException.conflict("CONTACT_LIMIT", "You can have up to 3 contacts");

        Contact c = new Contact(userId, name.trim(), phone, Tokens.random(32));
        if (makeDefault || existing.isEmpty()) {
            existing.forEach(o -> o.setDefault(false));
            c.setDefault(true);
        }
        return dto(contacts.save(c));
    }

    @Transactional
    public ContactDto update(UUID userId, UUID contactId, String name, Boolean makeDefault) {
        Contact c = owned(userId, contactId);
        if (name != null && !name.isBlank()) c.setName(name.trim());
        if (Boolean.TRUE.equals(makeDefault)) {
            contacts.findByUserIdOrderByCreatedAtAsc(userId).forEach(o -> o.setDefault(o.getId().equals(contactId)));
        }
        return dto(contacts.save(c));
    }

    @Transactional
    public void remove(UUID userId, UUID contactId) {
        contacts.findByIdAndUserId(contactId, userId).ifPresent(c -> {
            boolean wasDefault = c.isDefault();
            contacts.delete(c);
            contacts.flush();
            if (wasDefault) {
                contacts.findByUserIdOrderByCreatedAtAsc(userId).stream().findFirst().ifPresent(n -> n.setDefault(true));
            }
        });
    }

    /** New link token; the old link stops working immediately. */
    @Transactional
    public ContactDto resetLink(UUID userId, UUID contactId) {
        Contact c = owned(userId, contactId);
        c.setLinkToken(Tokens.random(32));
        return dto(contacts.save(c));
    }

    private Contact owned(UUID userId, UUID contactId) {
        return contacts.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> ApiException.notFound("CONTACT_NOT_FOUND", "Contact not found"));
    }

    private ContactDto dto(Contact c) {
        return new ContactDto(c.getId(), c.getName(), c.getPhone(), c.isDefault(),
                props.publicBaseUrl().replaceAll("/$", "") + "/t/" + c.getLinkToken());
    }

    // ---------------- Sessions ----------------

    /** Idempotent by clientSessionId, so a retried "start" never creates two sessions. */
    @Transactional
    public SessionDto start(UUID userId, String clientSessionId) {
        var existing = sessions.findByUserIdAndClientSessionId(userId, clientSessionId);
        if (existing.isPresent()) return dto(existing.get());

        sessions.activeFor(userId).forEach(s -> s.end(Instant.now())); // one live session at a time
        SharingSession s = sessions.save(new SharingSession(userId, clientSessionId, Instant.now()));

        notifyContactsWithApp(userId, s);
        return dto(s);
    }

    /**
     * Batch of points (sent about every 30 s). Duplicate timestamps are ignored, so retries are safe.
     * Points are not backfilled after a connection drop: anything older than the newest point is skipped.
     */
    @Transactional
    public PointsResult addPoints(UUID userId, UUID sessionId, List<Point> points) {
        SharingSession s = sessions.lockOwned(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Sharing session not found"));
        if (!s.isActive()) throw ApiException.conflict("SESSION_ENDED", "This sharing session has ended");

        List<Point> sorted = points.stream()
                .filter(p -> Math.abs(p.lat()) <= 90 && Math.abs(p.lng()) <= 180)
                .sorted(Comparator.comparingLong(Point::t)).toList();

        Point last = lastPoint(sessionId);
        int accepted = 0;
        for (Point p : sorted) {
            if (last != null && p.t() <= last.t()) continue;
            int n = jdbc.update("INSERT INTO walk_safe_points (session_id, recorded_at, lat, lng) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT DO NOTHING", sessionId, p.t(), p.lat(), p.lng());
            if (n == 1) {
                if (last != null) {
                    double d = Geo.metres(last.lat(), last.lng(), p.lat(), p.lng());
                    if (d < 500) s.addDistance(d); // ignore GPS jumps after a resume
                }
                last = p;
                accepted++;
            }
        }
        if (accepted > 0) s.setLastPointAt(Instant.ofEpochMilli(last.t()));
        sessions.save(s);
        return new PointsResult(accepted, round2(s.getDistanceM() / 1000));
    }

    @Transactional
    public SessionDto end(UUID userId, UUID sessionId) {
        SharingSession s = sessions.lockOwned(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Sharing session not found"));
        if (s.isActive()) {
            s.end(Instant.now());
            sessions.save(s);
        }
        return dto(s);
    }

    private Point lastPoint(UUID sessionId) {
        List<Point> r = jdbc.query("SELECT lat, lng, recorded_at FROM walk_safe_points WHERE session_id = ? "
                        + "ORDER BY recorded_at DESC LIMIT 1",
                (rs, i) -> new Point(rs.getDouble(1), rs.getDouble(2), rs.getLong(3)), sessionId);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<LatLng> pointsSince(UUID sessionId, long sinceMs) {
        return jdbc.query("SELECT lat, lng FROM walk_safe_points WHERE session_id = ? AND recorded_at >= ? ORDER BY recorded_at",
                (rs, i) -> new LatLng(rs.getDouble(1), rs.getDouble(2)), sessionId, sinceMs);
    }

    public List<LatLng> allPoints(UUID sessionId) {
        return pointsSince(sessionId, 0);
    }

    private void notifyContactsWithApp(UUID userId, SharingSession s) {
        String name = profiles.find(userId).map(p -> p.getName()).filter(n -> n != null).orElse("Your contact");
        for (Contact c : contacts.findByUserIdOrderByCreatedAtAsc(userId)) {
            users.findByPhone(c.getPhone()).map(User::getId).ifPresent(contactUserId ->
                    push.send(contactUserId, name + " started a walk",
                            "Tap to follow their walk live.",
                            Map.of("type", "walk_safe_started", "sessionId", s.getId().toString(),
                                    "token", c.getLinkToken())));
        }
    }

    private SessionDto dto(SharingSession s) {
        return new SessionDto(s.getId(), s.getStatus(), s.getStartedAt().toEpochMilli(),
                s.getEndedAt() == null ? null : s.getEndedAt().toEpochMilli(), round2(s.getDistanceM() / 1000));
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
