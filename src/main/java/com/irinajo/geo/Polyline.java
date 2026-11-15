package com.irinajo.geo;

import java.util.ArrayList;
import java.util.List;

/** Google encoded polyline, precision 5. */
public final class Polyline {
    private Polyline() {}

    public static List<LatLng> decode(String encoded) {
        List<LatLng> out = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return out;
        int index = 0, lat = 0, lng = 0, len = encoded.length();
        while (index < len) {
            int[] r = next(encoded, index);
            lat += r[0];
            index = r[1];
            if (index >= len) break;
            r = next(encoded, index);
            lng += r[0];
            index = r[1];
            out.add(new LatLng(lat / 1e5, lng / 1e5));
        }
        return out;
    }

    private static int[] next(String s, int index) {
        int result = 0, shift = 0, b;
        do {
            b = s.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < s.length());
        int delta = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
        return new int[]{delta, index};
    }

    public static String encode(List<LatLng> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0, prevLng = 0;
        for (LatLng p : points) {
            long lat = Math.round(p.lat() * 1e5);
            long lng = Math.round(p.lng() * 1e5);
            write(sb, lat - prevLat);
            write(sb, lng - prevLng);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    private static void write(StringBuilder sb, long v) {
        v = v < 0 ? ~(v << 1) : (v << 1);
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }
}
