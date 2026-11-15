package com.irinajo.walksafe;

import com.irinajo.auth.OtpChallengeRepository;
import com.irinajo.auth.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Data minimisation (NDPA 2023): delete what we no longer need. */
@Component
public class RetentionJob {
    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final JdbcTemplate jdbc;
    private final SessionRepository sessions;
    private final OtpChallengeRepository otps;
    private final RefreshTokenRepository refreshTokens;

    public RetentionJob(JdbcTemplate jdbc, SessionRepository sessions, OtpChallengeRepository otps,
                        RefreshTokenRepository refreshTokens) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.otps = otps;
        this.refreshTokens = refreshTokens;
    }

    /** Ends sessions whose phone stopped sending points for 30 minutes (app killed, battery died). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void endAbandonedSessions() {
        var stale = sessions.stale(Instant.now().minus(Duration.ofMinutes(30)));
        for (SharingSession s : stale) {
            s.end(s.getLastPointAt() != null ? s.getLastPointAt() : Instant.now());
            sessions.save(s);
        }
        if (!stale.isEmpty()) log.info("Auto-ended {} Walk Safe sessions", stale.size());
    }

    /** Shared location points are deleted 24 hours after the walk ends. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 90_000)
    public void deleteOldPoints() {
        int n = jdbc.update("""
                DELETE FROM walk_safe_points p USING walk_safe_sessions s
                 WHERE p.session_id = s.id AND s.status = 'ENDED' AND s.ended_at < now() - interval '24 hours'
                """);
        if (n > 0) log.info("Deleted {} Walk Safe points older than 24h", n);
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void housekeeping() {
        otps.deleteExpired(Instant.now().minus(Duration.ofHours(1)));
        refreshTokens.deleteOld(Instant.now().minus(Duration.ofDays(7)));
        // Session rows themselves (no locations left) are kept 30 days for the "finished at" message, then removed.
        jdbc.update("DELETE FROM walk_safe_sessions WHERE status = 'ENDED' AND ended_at < now() - interval '30 days'");
    }
}
