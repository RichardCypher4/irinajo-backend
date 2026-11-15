package com.irinajo.explore;

import com.fasterxml.jackson.databind.JsonNode;
import com.irinajo.common.ApiException;
import com.irinajo.config.AppProperties;
import com.irinajo.geo.Geo;
import com.irinajo.geo.GeocodingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ExploreService {
    private static final Logger log = LoggerFactory.getLogger(ExploreService.class);
    private static final Map<String, String> CATEGORIES = Map.of(
            "nearby", "", "parks", "PARKS", "cafes", "CAFES", "landmarks", "LANDMARKS", "quiet", "QUIET");

    public record Point(double lat, double lng) {}

    public record WalkDto(String id, String name, String category, double distanceKm, int estMinutes,
                          String difficulty, String safety, String polyline, Point start,
                          double distanceFromYouKm, LocalDate ratedOn, String source) {}

    public record Place(String name, String type, double lat, double lng) {}

    public record SearchResult(String title, List<WalkDto> walks, List<Place> places) {}

    private final CuratedWalkRepository walks;
    private final SafetyRatingService safety;
    private final GeocodingService geocoding;
    private final RestClient http;
    private final AppProperties props;

    public ExploreService(CuratedWalkRepository walks, SafetyRatingService safety, GeocodingService geocoding,
                          RestClient http, AppProperties props) {
        this.walks = walks;
        this.safety = safety;
        this.geocoding = geocoding;
        this.http = http;
        this.props = props;
    }

    public List<WalkDto> nearby(double lat, double lng, String category, int hour, int limit) {
        String key = category == null ? "nearby" : category.toLowerCase(Locale.ROOT);
        if (!CATEGORIES.containsKey(key)) throw ApiException.badRequest("CATEGORY_INVALID", "Unknown category");
        String dbCat = CATEGORIES.get(key).isEmpty() ? null : CATEGORIES.get(key);
        double radiusKm = dbCat == null ? 3 : 10;
        return inRadius(lat, lng, radiusKm, dbCat).stream()
                .map(w -> dto(w, lat, lng, hour))
                .filter(w -> w.distanceFromYouKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(WalkDto::distanceFromYouKm))
                .limit(Math.min(Math.max(limit, 1), 30))
                .toList();
    }

    /** Walks first ("3 walks around Freedom Park"), then places. */
    public SearchResult search(String q, double lat, double lng, int hour) {
        String query = q == null ? "" : q.trim();
        if (query.length() < 2) throw ApiException.badRequest("QUERY_TOO_SHORT", "Type at least 2 characters");
        double d = 0.2;
        List<WalkDto> found = new ArrayList<>(walks.search(query, lat - d, lat + d, lng - d, lng + d).stream()
                .map(w -> dto(w, lat, lng, hour))
                .sorted(Comparator.comparingDouble(WalkDto::distanceFromYouKm)).limit(10).toList());

        List<Place> places = new ArrayList<>();
        JsonNode res = geocoding.searchPlaces(query, lat, lng);
        if (res != null && res.isArray()) {
            for (JsonNode p : res) {
                String name = p.path("name").asText("");
                if (name.isEmpty()) name = p.path("display_name").asText("").split(",")[0];
                places.add(new Place(name, p.path("type").asText(null), p.path("lat").asDouble(), p.path("lon").asDouble()));
            }
        }
        // If the query is a place with no walk names matching, show walks around that place.
        if (found.isEmpty() && !places.isEmpty()) {
            Place top = places.get(0);
            found.addAll(inRadius(top.lat(), top.lng(), 1.5, null).stream()
                    .map(w -> dto(w, lat, lng, hour)).limit(10).toList());
            if (!found.isEmpty()) {
                return new SearchResult(found.size() + (found.size() == 1 ? " walk" : " walks") + " around " + top.name(), found, places);
            }
        }
        String title = found.size() + (found.size() == 1 ? " walk" : " walks") + " for \"" + query + "\"";
        return new SearchResult(title, found, places);
    }

    /**
     * Up to 3 loops starting and ending at the user's location, each within 10% of the remaining
     * distance (minimum 1.5 km), ranked by safety for that hour. After dark, Take Care routes are dropped.
     */
    public List<WalkDto> suggest(double lat, double lng, double remainingKm, int hour) {
        double target = Math.max(remainingKm, 1.5);
        double lo = target * 0.9, hi = target * 1.1;
        boolean dark = SafetyRatingService.afterDark(hour);

        List<WalkDto> out = new ArrayList<>(inRadius(lat, lng, 0.4, null).stream()
                .filter(CuratedWalk::isLoop)
                .filter(w -> w.getDistanceKm().doubleValue() >= lo && w.getDistanceKm().doubleValue() <= hi)
                .map(w -> dto(w, lat, lng, hour))
                .toList());

        String ghKey = props.routing().graphhopperKey();
        if (out.size() < 3 && ghKey != null && !ghKey.isBlank()) {
            for (int seed = 0; seed < 6 && out.size() < 6; seed++) {
                WalkDto g = generatedLoop(lat, lng, target, seed, hour, ghKey);
                if (g != null && g.distanceKm() >= lo && g.distanceKm() <= hi) out.add(g);
            }
        }
        return out.stream()
                .filter(w -> !(dark && SafetyRatingService.TAKE_CARE.equals(w.safety())))
                .sorted(Comparator.comparingInt((WalkDto w) -> SafetyRatingService.rank(w.safety()))
                        .thenComparingDouble(w -> Math.abs(w.distanceKm() - target)))
                .limit(3)
                .toList();
    }

    private WalkDto generatedLoop(double lat, double lng, double targetKm, int seed, int hour, String key) {
        try {
            JsonNode res = http.get()
                    .uri("https://graphhopper.com/api/1/route?point={p}&profile=foot&algorithm=round_trip"
                                    + "&round_trip.distance={d}&round_trip.seed={s}&points_encoded=true&instructions=false&key={k}",
                            lat + "," + lng, (int) (targetKm * 1000), seed, key)
                    .retrieve().body(JsonNode.class);
            JsonNode path = res == null ? null : res.path("paths").path(0);
            if (path == null || path.isMissingNode()) return null;
            double km = Math.round(path.path("distance").asDouble() / 10) / 100.0;
            String poly = path.path("points").asText();
            int minutes = (int) Math.round(km * 12); // ~5 km/h walking pace
            String level = safety.rate(SafetyRatingService.FAIR, SafetyRatingService.FAIR, poly, hour);
            return new WalkDto("gen-" + UUID.randomUUID(), "Loop from here", null, km, minutes,
                    km <= 3 ? "EASY" : km <= 6 ? "MODERATE" : "HARD", level, poly, new Point(lat, lng), 0,
                    LocalDate.now(), "generated");
        } catch (Exception e) {
            log.warn("GraphHopper round trip failed: {}", e.getMessage());
            return null;
        }
    }

    private List<CuratedWalk> inRadius(double lat, double lng, double radiusKm, String category) {
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return walks.inBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng, category).stream()
                .filter(w -> Geo.metres(lat, lng, w.getStartLat(), w.getStartLng()) <= radiusKm * 1000)
                .toList();
    }

    private WalkDto dto(CuratedWalk w, double lat, double lng, int hour) {
        double away = Geo.metres(lat, lng, w.getStartLat(), w.getStartLng()) / 1000;
        String level = safety.rate(w.getSafetyDay(), w.getSafetyNight(), w.getPolyline(), hour);
        return new WalkDto(w.getId().toString(), w.getName(), w.getCategory(), w.getDistanceKm().doubleValue(),
                w.getEstMinutes(), w.getDifficulty(), level, w.getPolyline(), new Point(w.getStartLat(), w.getStartLng()),
                Math.round(away * 100) / 100.0, w.getRatedOn(), "curated");
    }
}
