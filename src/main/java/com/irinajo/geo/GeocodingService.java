package com.irinajo.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.irinajo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenStreetMap-based naming: Nominatim reverse geocoding for the area name and Overpass for
 * landmarks along the route. The public Nominatim server allows ~1 request/second with a real
 * User-Agent; for production volume, self-host Nominatim or switch to a paid OSM geocoder/Mapbox.
 */
@Service
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final String[] AREA_KEYS = {"suburb", "neighbourhood", "quarter", "city_district", "town", "village", "city"};

    private final RestClient http;
    private final AppProperties.Geocoding cfg;

    /** Small LRU cache by ~110 m cell: many walks start from the same streets. */
    private final Map<String, String> areaCache = Collections.synchronizedMap(new LinkedHashMap<>(1000, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 5000; }
    });

    public GeocodingService(RestClient http, AppProperties props) {
        this.http = http;
        this.cfg = props.geocoding();
    }

    public boolean enabled() { return cfg.enabled(); }

    public String areaName(double lat, double lng) {
        String cell = Geo.cell(lat, lng);
        String cached = areaCache.get(cell);
        if (cached != null) return cached.isEmpty() ? null : cached;

        JsonNode res = http.get()
                .uri(cfg.nominatimUrl() + "/reverse?format=jsonv2&zoom=16&addressdetails=1&accept-language=en&lat={lat}&lon={lng}", lat, lng)
                .header("User-Agent", cfg.userAgent())
                .retrieve()
                .body(JsonNode.class);
        String area = null;
        if (res != null && res.has("address")) {
            JsonNode a = res.get("address");
            for (String k : AREA_KEYS) {
                if (a.hasNonNull(k)) { area = a.get(k).asText(); break; }
            }
        }
        areaCache.put(cell, area == null ? "" : area);
        return area;
    }

    /** Up to 3 named landmarks within 60 m of the route. */
    public List<String> landmarks(List<LatLng> route) {
        if (route.size() < 2) return List.of();
        List<LatLng> pts = Geo.sample(route, 25);
        String line = pts.stream().map(p -> String.format("%.5f,%.5f", p.lat(), p.lng())).collect(Collectors.joining(","));
        String around = "(around:60," + line + ")";
        String q = "[out:json][timeout:10];("
                + "node" + around + "[name][tourism~\"attraction|museum|monument|viewpoint|artwork\"];"
                + "node" + around + "[name][historic];"
                + "way" + around + "[name][leisure=park];"
                + "way" + around + "[name][historic];"
                + "way" + around + "[name][tourism~\"attraction|museum\"];"
                + ");out tags 10;";
        JsonNode res = http.post()
                .uri(cfg.overpassUrl())
                .header("User-Agent", cfg.userAgent())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("data=" + URLEncoder.encode(q, StandardCharsets.UTF_8))
                .retrieve()
                .body(JsonNode.class);
        List<String> names = new ArrayList<>();
        if (res != null && res.has("elements")) {
            for (JsonNode el : res.get("elements")) {
                JsonNode tags = el.get("tags");
                if (tags == null) continue;
                String name = tags.hasNonNull("name:en") ? tags.get("name:en").asText() : tags.path("name").asText(null);
                if (name != null && !names.contains(name)) names.add(name);
                if (names.size() == 3) break;
            }
        }
        return names;
    }

    public JsonNode searchPlaces(String q, double lat, double lng) {
        double d = 0.15; // ~16 km box around the user
        String viewbox = (lng - d) + "," + (lat + d) + "," + (lng + d) + "," + (lat - d);
        try {
            return http.get()
                    .uri(cfg.nominatimUrl() + "/search?format=jsonv2&limit=5&countrycodes=ng&bounded=1&viewbox={vb}&q={q}", viewbox, q)
                    .header("User-Agent", cfg.userAgent())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.warn("Place search failed: {}", e.getMessage());
            return null;
        }
    }
}
