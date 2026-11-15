package com.irinajo.explore;

import org.springframework.stereotype.Service;

/**
 * Safety level for a route at a given hour: Good, Fair or Take Care.
 *
 * The base day/night rating on each curated walk is set from the three inputs in the brief
 * (lighting from OSM lit tags and sunset, foot traffic, footpaths) by the offline rating
 * pipeline or by an admin. Live Felt Safe answers then nudge it per street cell and hour:
 * many "unsafe" answers downgrade it; consistently "safe" answers can lift Fair to Good.
 */
@Service
public class SafetyRatingService {
    public static final String GOOD = "GOOD", FAIR = "FAIR", TAKE_CARE = "TAKE_CARE";

    private final SafetyFeedbackService feedback;

    public SafetyRatingService(SafetyFeedbackService feedback) {
        this.feedback = feedback;
    }

    /** Nigeria sits near the equator: sunset is roughly 18:30-19:00 all year. */
    public static boolean afterDark(int hour) {
        return hour >= 19 || hour < 6;
    }

    public String rate(String baseDay, String baseNight, String polyline, int hour) {
        String base = afterDark(hour) ? baseNight : baseDay;
        if (base == null) base = FAIR;
        var t = feedback.tally(polyline, hour);
        int total = t.safe() + t.unsafe();
        if (total >= 5) {
            double unsafeShare = (double) t.unsafe() / total;
            if (unsafeShare >= 0.4) return downgrade(base);
            if (total >= 10 && unsafeShare <= 0.1 && FAIR.equals(base)) return GOOD;
        }
        return base;
    }

    public static int rank(String level) {
        return switch (level) { case GOOD -> 0; case FAIR -> 1; default -> 2; };
    }

    private static String downgrade(String l) {
        return GOOD.equals(l) ? FAIR : TAKE_CARE;
    }
}
