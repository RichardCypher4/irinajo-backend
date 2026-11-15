package com.irinajo.walksafe;

import com.irinajo.config.AppProperties;
import com.irinajo.geo.Geo;
import com.irinajo.geo.LatLng;
import com.irinajo.geo.Polyline;
import com.irinajo.user.ProfileService;
import com.irinajo.user.User;
import com.irinajo.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public tracking page for trusted contacts without the app: no sign-in, tiny HTML, refreshes
 * every 30 s, hidden from search engines. The walk's first 200 m is never shown (front door).
 */
@RestController
public class TrackingController {
    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private enum State { LIVE, IDLE, ENDED }

    private record View(State state, String name, String phone, SharingSession session) {}

    private final ContactRepository contacts;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final ProfileService profiles;
    private final WalkSafeService walkSafe;
    private final RestClient http;
    private final AppProperties props;
    private final Map<UUID, Map.Entry<Long, byte[]>> mapCache = new ConcurrentHashMap<>();

    public TrackingController(ContactRepository contacts, SessionRepository sessions, UserRepository users,
                              ProfileService profiles, WalkSafeService walkSafe, RestClient http, AppProperties props) {
        this.contacts = contacts;
        this.sessions = sessions;
        this.users = users;
        this.profiles = profiles;
        this.walkSafe = walkSafe;
        this.http = http;
        this.props = props;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nDisallow: /t/\nDisallow: /account/\n";
    }

    @GetMapping(value = "/t/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String token) {
        Optional<View> v = view(token);
        if (v.isEmpty()) {
            return html(HttpStatus.NOT_FOUND, shell("Link not working", "<p>This link no longer works. Ask for a new one.</p>", false));
        }
        View view = v.get();
        String name = esc(view.name());
        String body;
        switch (view.state()) {
            case LIVE -> {
                SharingSession s = view.session();
                String updated = s.getLastPointAt() == null ? "waiting for first location"
                        : TIME.format(s.getLastPointAt().atZone(LAGOS));
                String img = props.maps().mapboxToken() == null || props.maps().mapboxToken().isBlank() ? ""
                        : "<img src=\"/t/" + esc(token) + "/map.png?v=" + (s.getLastPointAt() == null ? 0 : s.getLastPointAt().toEpochMilli())
                          + "\" width=\"400\" height=\"260\" alt=\"Map of the last 15 minutes\">";
                String call = view.phone() == null ? "" : "<a class=\"call\" href=\"tel:" + esc(view.phone()) + "\">Call " + name + "</a>";
                body = "<h1>" + name + " is walking</h1>" + img
                        + "<p>Last update: <b>" + updated + "</b><br>Distance so far: <b>"
                        + String.format(Locale.ENGLISH, "%.2f", s.getDistanceM() / 1000) + " km</b></p>" + call;
            }
            case ENDED -> body = "<h1>" + name + " finished their walk at "
                    + TIME.format(view.session().getEndedAt().atZone(LAGOS)).toLowerCase(Locale.ENGLISH) + ".</h1>";
            default -> body = "<h1>" + name + " isn't walking right now.</h1>";
        }
        return html(HttpStatus.OK, shell(view.name() + " · Irin Ajo", body, view.state() != State.ENDED));
    }

    /** JSON for contacts who have the app (in-app live view). */
    @GetMapping("/t/{token}/live.json")
    public ResponseEntity<Map<String, Object>> live(@PathVariable String token) {
        Optional<View> v = view(token);
        if (v.isEmpty()) return ResponseEntity.notFound().build();
        View view = v.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", view.state().name().toLowerCase());
        m.put("name", view.name());
        if (view.session() != null && view.state() != State.IDLE) {
            SharingSession s = view.session();
            m.put("distanceKm", WalkSafeService.round2(s.getDistanceM() / 1000));
            if (s.getLastPointAt() != null) m.put("lastUpdate", s.getLastPointAt().toEpochMilli());
            if (s.getEndedAt() != null) m.put("endedAt", s.getEndedAt().toEpochMilli());
            if (view.state() == State.LIVE) m.put("polyline", Polyline.encode(visibleRoute(s)));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex, nofollow").body(m);
    }

    /** Static map of the last 15 minutes. Proxied so the Mapbox token never leaves the server. */
    @GetMapping(value = "/t/{token}/map.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> map(@PathVariable String token) {
        String mapbox = props.maps().mapboxToken();
        Optional<View> v = view(token);
        if (v.isEmpty() || v.get().state() != State.LIVE || mapbox == null || mapbox.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        SharingSession s = v.get().session();
        long version = s.getLastPointAt() == null ? 0 : s.getLastPointAt().toEpochMilli();
        var cached = mapCache.get(s.getId());
        if (cached != null && cached.getKey() == version) return png(cached.getValue());

        List<LatLng> route = Geo.sample(visibleRoute(s), 80);
        if (route.isEmpty()) return ResponseEntity.notFound().build();
        LatLng last = route.get(route.size() - 1);
        String overlay = (route.size() >= 2
                ? "path-4+1f7a4d-0.9(" + URLEncoder.encode(Polyline.encode(route), StandardCharsets.UTF_8) + "),"
                : "")
                + String.format(Locale.ENGLISH, "pin-s+e4572e(%.5f,%.5f)", last.lng(), last.lat());
        String url = "https://api.mapbox.com/styles/v1/mapbox/streets-v12/static/" + overlay
                + "/auto/400x260?padding=30&access_token=" + mapbox;
        try {
            byte[] img = http.get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
            if (mapCache.size() > 1000) mapCache.clear();
            mapCache.put(s.getId(), Map.entry(version, img));
            return png(img);
        } catch (Exception e) {
            log.warn("Static map failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /** Last 15 minutes of the session, never including the first 200 m of the walk. */
    private List<LatLng> visibleRoute(SharingSession s) {
        List<LatLng> all = walkSafe.allPoints(s.getId());
        int startIdx = 0;
        double d = 0;
        while (startIdx < all.size() - 1 && d < 200) {
            d += Geo.metres(all.get(startIdx), all.get(startIdx + 1));
            startIdx++;
        }
        if (d < 200) return List.of();
        long from = (s.getLastPointAt() == null ? Instant.now() : s.getLastPointAt()).toEpochMilli() - Duration.ofMinutes(15).toMillis();
        List<LatLng> recent = walkSafe.pointsSince(s.getId(), from);
        // keep only the recent points that are also beyond the trimmed start
        List<LatLng> allowed = new ArrayList<>(all.subList(startIdx, all.size()));
        return recent.stream().filter(allowed::contains).toList();
    }

    private Optional<View> view(String token) {
        if (token == null || token.length() < 32 || token.length() > 64) return Optional.empty();
        return contacts.findByLinkToken(token).map(c -> {
            UUID userId = c.getUserId();
            String name = profiles.find(userId).map(p -> p.getName()).filter(n -> n != null).orElse("Your contact");
            String phone = users.findById(userId).map(User::getPhone).orElse(null);
            SharingSession s = sessions.findFirstByUserIdOrderByStartedAtDesc(userId).orElse(null);
            if (s == null) return new View(State.IDLE, name, phone, null);
            if (s.isActive()) return new View(State.LIVE, name, phone, s);
            if (s.getEndedAt() != null && s.getEndedAt().isAfter(Instant.now().minus(Duration.ofHours(12)))) {
                return new View(State.ENDED, name, phone, s);
            }
            return new View(State.IDLE, name, phone, null);
        });
    }

    private static ResponseEntity<byte[]> png(byte[] img) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(25)).cachePrivate())
                .header("X-Robots-Tag", "noindex, nofollow").body(img);
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }

    private static String shell(String title, String body, boolean refresh) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"robots\" content=\"noindex,nofollow\">"
                + (refresh ? "<meta http-equiv=\"refresh\" content=\"30\">" : "")
                + "<title>" + esc(title) + "</title><style>"
                + "body{font-family:system-ui,sans-serif;max-width:440px;margin:24px auto;padding:0 16px;color:#1d2a22}"
                + "h1{font-size:22px}img{width:100%;height:auto;border-radius:12px;background:#e8efe9}"
                + ".call{display:block;text-align:center;background:#1f7a4d;color:#fff;padding:14px;border-radius:10px;"
                + "text-decoration:none;font-weight:600}small{color:#667}</style></head><body>"
                + body + "<p><small>Irin Ajo · Every Step, a Journey.</small></p></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
