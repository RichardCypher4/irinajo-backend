package com.irinajo.explore;

import com.fasterxml.jackson.databind.JsonNode;
import com.irinajo.common.ApiException;
import com.irinajo.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weather proxy, cached by area (~1 km) and hour; the provider key stays on the server.
 * Defaults to Open-Meteo (set WEATHER_API_KEY for their commercial endpoint).
 */
@Service
public class WeatherService {

    public record Weather(double tempC, double feelsLikeC, double todayMaxC, double todayMinC,
                          double uvIndexMax, int rainChancePct, String heatLevel, long fetchedAt) {}

    private final RestClient http;
    private final AppProperties.Weather cfg;
    private final Map<String, Weather> cache = new ConcurrentHashMap<>();

    public WeatherService(RestClient http, AppProperties props) {
        this.http = http;
        this.cfg = props.weather();
    }

    public Weather get(double lat, double lng) {
        String hourKey = ZonedDateTime.now(ZoneId.of("Africa/Lagos")).withMinute(0).withSecond(0).withNano(0).toString();
        String key = String.format(Locale.ENGLISH, "%.2f,%.2f@%s", lat, lng, hourKey);
        Weather hit = cache.get(key);
        if (hit != null) return hit;

        String base = cfg.baseUrl();
        String url = base + "?latitude={lat}&longitude={lng}&timezone={tz}&forecast_days=1"
                + "&current=temperature_2m,apparent_temperature"
                + "&daily=temperature_2m_max,temperature_2m_min,uv_index_max,precipitation_probability_max"
                + (cfg.apiKey() == null || cfg.apiKey().isBlank() ? "" : "&apikey=" + cfg.apiKey());
        JsonNode r;
        try {
            r = http.get().uri(url, Math.round(lat * 100) / 100.0, Math.round(lng * 100) / 100.0, "Africa/Lagos")
                    .retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw ApiException.upstream("WEATHER_UNAVAILABLE", "Weather is unavailable right now");
        }
        if (r == null) throw ApiException.upstream("WEATHER_UNAVAILABLE", "Weather is unavailable right now");

        double feels = r.path("current").path("apparent_temperature").asDouble();
        Weather w = new Weather(
                r.path("current").path("temperature_2m").asDouble(),
                feels,
                r.path("daily").path("temperature_2m_max").path(0).asDouble(),
                r.path("daily").path("temperature_2m_min").path(0).asDouble(),
                r.path("daily").path("uv_index_max").path(0).asDouble(),
                r.path("daily").path("precipitation_probability_max").path(0).asInt(),
                feels >= 41 ? "danger" : feels >= 35 ? "high" : feels >= 32 ? "caution" : "ok",
                System.currentTimeMillis());
        if (cache.size() > 20_000) cache.clear();
        cache.put(key, w);
        return w;
    }
}
