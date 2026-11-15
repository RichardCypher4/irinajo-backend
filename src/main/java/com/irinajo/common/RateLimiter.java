package com.irinajo.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window, in-memory rate limiter. Good for a single Railway instance.
 * If you scale to several replicas, move these counters to Redis (INCR + EXPIRE).
 */
@Component
public class RateLimiter {

    private record Window(long startEpochSec, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @return seconds to wait if the limit is exceeded, or 0 if the call is allowed.
     */
    public int hit(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis() / 1000;
        long start = now - (now % windowSeconds);
        Window w = windows.compute(key, (k, old) ->
                (old == null || old.startEpochSec() != start) ? new Window(start, new AtomicInteger()) : old);
        int n = w.count().incrementAndGet();
        if (n > limit) return (int) (start + windowSeconds - now);
        return 0;
    }

    public void check(String key, int limit, int windowSeconds, String code) {
        int wait = hit(key, limit, windowSeconds);
        if (wait > 0) throw ApiException.tooMany(code, "Too many requests, try again later", wait);
    }

    @Scheduled(fixedDelay = 600_000)
    void evict() {
        long now = System.currentTimeMillis() / 1000;
        windows.entrySet().removeIf(e -> now - e.getValue().startEpochSec() > 7200);
    }
}
