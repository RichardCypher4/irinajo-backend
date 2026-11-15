package com.irinajo;

import com.irinajo.common.ApiException;
import com.irinajo.common.Phones;
import com.irinajo.geo.Geo;
import com.irinajo.geo.LatLng;
import com.irinajo.geo.Polyline;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnitTests {

    @Test
    void polylineRoundTrip() {
        String p = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
        List<LatLng> pts = Polyline.decode(p);
        assertEquals(3, pts.size());
        assertEquals(38.5, pts.get(0).lat(), 1e-9);
        assertEquals(p, Polyline.encode(pts));
    }

    @Test
    void trimHidesBothEnds() {
        List<LatLng> line = new ArrayList<>();
        for (int i = 0; i < 100; i++) line.add(new LatLng(6.5 + i * 0.0001, 3.37)); // ~11 m apart
        List<LatLng> trimmed = Geo.trim(line, 200, 200);
        assertTrue(Geo.metres(line.get(0), trimmed.get(0)) >= 200);
        assertTrue(Geo.metres(line.get(99), trimmed.get(trimmed.size() - 1)) >= 200);
    }

    @Test
    void nigerianNumbersOnly() {
        assertEquals("+2348031234567", Phones.normalise("08031234567"));
        assertEquals("+2349061234567", Phones.normalise("+234 906 123 4567"));
        assertThrows(ApiException.class, () -> Phones.normalise("+447700900123"));
        assertThrows(ApiException.class, () -> Phones.normalise("+2346031234567"));
    }
}
