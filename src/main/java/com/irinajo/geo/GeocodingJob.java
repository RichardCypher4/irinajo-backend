package com.irinajo.geo;

import com.irinajo.common.ChangeSequence;
import com.irinajo.common.UserLock;
import com.irinajo.sync.Journey;
import com.irinajo.sync.JourneyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * After upload, names each journey ("Morning walk in Yaba") and finds landmarks.
 * Results reach the phone through the normal /sync/changes feed (seq is bumped).
 * Runs one journey at a time to respect Nominatim's 1 request/second policy.
 */
@Component
public class GeocodingJob {
    private static final Logger log = LoggerFactory.getLogger(GeocodingJob.class);

    private final JourneyRepository journeys;
    private final GeocodingService geo;
    private final ChangeSequence seq;
    private final UserLock lock;
    private final TransactionTemplate tx;

    public GeocodingJob(JourneyRepository journeys, GeocodingService geo, ChangeSequence seq,
                        UserLock lock, TransactionTemplate tx) {
        this.journeys = journeys;
        this.geo = geo;
        this.seq = seq;
        this.lock = lock;
        this.tx = tx;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void run() {
        if (!geo.enabled()) return;
        List<Journey> batch = journeys.needingGeocode(PageRequest.of(0, 10));
        for (Journey j : batch) {
            String area = null;
            List<String> landmarks = List.of();
            try {
                if (j.getStartLat() != null) area = geo.areaName(j.getStartLat(), j.getStartLng());
                Thread.sleep(1100);
                if (j.getPolyline() != null) landmarks = geo.landmarks(Polyline.decode(j.getPolyline()));
                Thread.sleep(1100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Geocoding failed for a journey: {}", e.getMessage());
            }
            save(j, area, landmarks);
        }
    }

    private void save(Journey snapshot, String area, List<String> landmarks) {
        tx.executeWithoutResult(s -> {
            lock.forWrite(snapshot.getUserId());
            journeys.findById(new Journey.Key(snapshot.getUserId(), snapshot.getId())).ifPresent(j -> {
                if (j.isDeleted() || j.isGeocoded()) return;
                j.setArea(area);
                j.setLandmarks(landmarks);
                j.setGeocoded(true);
                j.setSeq(seq.next());
                journeys.save(j);
            });
        });
    }
}
