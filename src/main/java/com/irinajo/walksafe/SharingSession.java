package com.irinajo.walksafe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "walk_safe_sessions")
public class SharingSession {
    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "client_session_id")
    private String clientSessionId;

    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "last_point_at")
    private Instant lastPointAt;

    @Column(name = "distance_m")
    private double distanceM;

    protected SharingSession() {}

    public SharingSession(UUID userId, String clientSessionId, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.clientSessionId = clientSessionId;
        this.startedAt = startedAt;
        this.status = ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getClientSessionId() { return clientSessionId; }
    public String getStatus() { return status; }
    public boolean isActive() { return ACTIVE.equals(status); }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getLastPointAt() { return lastPointAt; }
    public void setLastPointAt(Instant t) { this.lastPointAt = t; }
    public double getDistanceM() { return distanceM; }
    public void addDistance(double m) { this.distanceM += m; }
    public void end(Instant at) {
        this.status = ENDED;
        this.endedAt = at;
    }
}
