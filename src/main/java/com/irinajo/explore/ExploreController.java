package com.irinajo.explore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
public class ExploreController {

    public record SuggestRequest(@DecimalMin("-90") @DecimalMax("90") double lat,
                                 @DecimalMin("-180") @DecimalMax("180") double lng,
                                 @DecimalMin("0") @DecimalMax("50") double distanceKm,
                                 @Min(0) @Max(23) Integer hour) {}

    private final ExploreService explore;
    private final WeatherService weather;

    public ExploreController(ExploreService explore, WeatherService weather) {
        this.explore = explore;
        this.weather = weather;
    }

    @GetMapping("/walks/nearby")
    public List<ExploreService.WalkDto> nearby(@RequestParam double lat, @RequestParam double lng,
                                               @RequestParam(defaultValue = "nearby") String category,
                                               @RequestParam(required = false) Integer hour,
                                               @RequestParam(defaultValue = "30") int limit) {
        return explore.nearby(lat, lng, category, hourOrNow(hour), limit);
    }

    @GetMapping("/walks/search")
    public ExploreService.SearchResult search(@RequestParam String q, @RequestParam double lat, @RequestParam double lng,
                                              @RequestParam(required = false) Integer hour) {
        return explore.search(q, lat, lng, hourOrNow(hour));
    }

    @PostMapping("/routes/suggest")
    public List<ExploreService.WalkDto> suggest(@Valid @RequestBody SuggestRequest body) {
        return explore.suggest(body.lat(), body.lng(), body.distanceKm(), hourOrNow(body.hour()));
    }

    @GetMapping("/weather")
    public ResponseEntity<WeatherService.Weather> weather(@RequestParam double lat, @RequestParam double lng) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)).cachePrivate())
                .body(weather.get(lat, lng));
    }

    private static int hourOrNow(Integer hour) {
        if (hour != null && hour >= 0 && hour <= 23) return hour;
        return ZonedDateTime.now(ZoneId.of("Africa/Lagos")).getHour();
    }
}
