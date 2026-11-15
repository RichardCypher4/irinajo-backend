package com.irinajo.explore;

import com.irinajo.geo.Geo;
import com.irinajo.geo.LatLng;
import com.irinajo.geo.Polyline;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns each journey's Felt Safe / Felt Unsafe answer into anonymous counts per ~110 m street cell
 * and hour of day. Only called for people who turned on "Share anonymised walks". No user IDs are stored.
 */
@Service
public class SafetyFeedbackService {
    public static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final JdbcTemplate jdbc;

    public SafetyFeedbackService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Applies a change of answer: removes the old vote (if any) and adds the new one (if any). */
    public void record(String polyline, long startedAt, Boolean oldAnswer, Boolean newAnswer) {
        if (polyline == null || java.util.Objects.equals(oldAnswer, newAnswer)) return;
        int hour = Instant.ofEpochMilli(startedAt).atZone(LAGOS).getHour();
        Set<String> cells = cells(polyline);
        for (String cell : cells) {
            if (oldAnswer != null) bump(cell, hour, oldAnswer, -1);
            if (newAnswer != null) bump(cell, hour, newAnswer, +1);
        }
    }

    private Set<String> cells(String polyline) {
        List<LatLng> pts = Geo.trim(Polyline.decode(polyline), 200, 200); // never near home
        Set<String> cells = new LinkedHashSet<>();
        for (LatLng p : pts) cells.add(Geo.cell(p.lat(), p.lng()));
        return cells;
    }

    private void bump(String cell, int hour, boolean safe, int delta) {
        String col = safe ? "safe_count" : "unsafe_count";
        jdbc.update("INSERT INTO safety_feedback (cell, hour, " + col + ") VALUES (?, ?, GREATEST(?, 0)) "
                + "ON CONFLICT (cell, hour) DO UPDATE SET " + col + " = GREATEST(safety_feedback." + col + " + ?, 0)",
                cell, (short) hour, delta, delta);
    }

    public record Tally(int safe, int unsafe) {}

    /** Sums answers for a route's cells within ±1 hour of the given hour. */
    public Tally tally(String polyline, int hour) {
        Set<String> cells = cells(polyline);
        if (cells.isEmpty()) return new Tally(0, 0);
        String placeholders = String.join(",", cells.stream().map(c -> "?").toList());
        Object[] args = new Object[cells.size() + 3];
        int i = 0;
        for (String c : cells) args[i++] = c;
        args[i++] = (short) ((hour + 23) % 24);
        args[i++] = (short) hour;
        args[i] = (short) ((hour + 1) % 24);
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(safe_count),0), COALESCE(SUM(unsafe_count),0) FROM safety_feedback "
                        + "WHERE cell IN (" + placeholders + ") AND hour IN (?, ?, ?)",
                (rs, n) -> new Tally(rs.getInt(1), rs.getInt(2)), args);
    }
}
