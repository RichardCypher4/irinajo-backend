package com.irinajo.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "journeys")
@IdClass(Journey.Key.class)
public class Journey {

    public static class Key implements Serializable {
        private UUID userId;
        private String id;

        public Key() {}
        public Key(UUID userId, String id) { this.userId = userId; this.id = id; }

        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(id, k.id);
        }
        @Override public int hashCode() { return Objects.hash(userId, id); }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    private String id;

    @Column(name = "started_at")
    private Long startedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    private Integer steps;

    @Column(name = "time_of_day")
    private String timeOfDay;

    private String area;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> landmarks = new ArrayList<>();

    @Column(name = "custom_name")
    private String customName;

    private String polyline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "km_times")
    private List<Integer> kmTimes = new ArrayList<>();

    @Column(name = "felt_safe")
    private Boolean feltSafe;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lng")
    private Double startLng;

    @Column(name = "updated_at")
    private long updatedAt;

    private boolean deleted;

    private boolean geocoded;

    private long seq;

    protected Journey() {}

    public Journey(UUID userId, String id) {
        this.userId = userId;
        this.id = id;
    }

    /** Turns this row into a tombstone and drops the location data we no longer need. */
    public void markDeleted(long updatedAt) {
        this.deleted = true;
        this.updatedAt = updatedAt;
        this.polyline = null;
        this.kmTimes = new ArrayList<>();
        this.landmarks = new ArrayList<>();
        this.customName = null;
        this.startLat = null;
        this.startLng = null;
        this.area = null;
        this.feltSafe = null;
        this.geocoded = true;
    }

    public UUID getUserId() { return userId; }
    public String getId() { return id; }
    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long v) { this.startedAt = v; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer v) { this.durationSec = v; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal v) { this.distanceKm = v; }
    public Integer getSteps() { return steps; }
    public void setSteps(Integer v) { this.steps = v; }
    public String getTimeOfDay() { return timeOfDay; }
    public void setTimeOfDay(String v) { this.timeOfDay = v; }
    public String getArea() { return area; }
    public void setArea(String v) { this.area = v; }
    public List<String> getLandmarks() { return landmarks; }
    public void setLandmarks(List<String> v) { this.landmarks = v; }
    public String getCustomName() { return customName; }
    public void setCustomName(String v) { this.customName = v; }
    public String getPolyline() { return polyline; }
    public void setPolyline(String v) { this.polyline = v; }
    public List<Integer> getKmTimes() { return kmTimes; }
    public void setKmTimes(List<Integer> v) { this.kmTimes = v; }
    public Boolean getFeltSafe() { return feltSafe; }
    public void setFeltSafe(Boolean v) { this.feltSafe = v; }
    public Double getStartLat() { return startLat; }
    public void setStartLat(Double v) { this.startLat = v; }
    public Double getStartLng() { return startLng; }
    public void setStartLng(Double v) { this.startLng = v; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long v) { this.updatedAt = v; }
    public boolean isDeleted() { return deleted; }
    public boolean isGeocoded() { return geocoded; }
    public void setGeocoded(boolean v) { this.geocoded = v; }
    public long getSeq() { return seq; }
    public void setSeq(long v) { this.seq = v; }
}
