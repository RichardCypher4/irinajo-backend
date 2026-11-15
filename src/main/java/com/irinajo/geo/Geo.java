package com.irinajo.geo;

import java.util.ArrayList;
import java.util.List;

public final class Geo {
    private static final double R = 6_371_000d;

    private Geo() {}

    public static double metres(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    public static double metres(LatLng a, LatLng b) {
        return metres(a.lat(), a.lng(), b.lat(), b.lng());
    }

    /** Removes the first {@code headM} and last {@code tailM} metres so nobody's front door is exposed. */
    public static List<LatLng> trim(List<LatLng> pts, double headM, double tailM) {
        if (pts.size() < 2) return List.of();
        int start = 0;
        double d = 0;
        while (start < pts.size() - 1 && d < headM) {
            d += metres(pts.get(start), pts.get(start + 1));
            start++;
        }
        int end = pts.size() - 1;
        d = 0;
        while (end > start && d < tailM) {
            d += metres(pts.get(end), pts.get(end - 1));
            end--;
        }
        if (end <= start) return List.of();
        return new ArrayList<>(pts.subList(start, end + 1));
    }

    /** ~110 m grid cell id, used to aggregate Felt Safe feedback without storing who walked. */
    public static String cell(double lat, double lng) {
        return String.format("%.3f,%.3f", lat, lng);
    }

    /** Keeps every n-th point so outbound requests (Overpass, Mapbox) stay small. */
    public static List<LatLng> sample(List<LatLng> pts, int max) {
        if (pts.size() <= max) return pts;
        List<LatLng> out = new ArrayList<>();
        double step = (double) (pts.size() - 1) / (max - 1);
        for (int i = 0; i < max; i++) out.add(pts.get((int) Math.round(i * step)));
        return out;
    }
}
