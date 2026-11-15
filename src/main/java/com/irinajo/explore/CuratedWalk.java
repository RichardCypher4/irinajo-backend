package com.irinajo.explore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Popular / curated walks, edited through the admin endpoints. */
@Entity
@Table(name = "curated_walks")
public class CuratedWalk {
    @Id
    private UUID id;
    private String name;
    private String category;            // PARKS, CAFES, LANDMARKS, QUIET
    @Column(name = "distance_km")
    private BigDecimal distanceKm;
    @Column(name = "est_minutes")
    private int estMinutes;
    private String difficulty;          // EASY, MODERATE, HARD
    @Column(name = "safety_day")
    private String safetyDay;           // GOOD, FAIR, TAKE_CARE
    @Column(name = "safety_night")
    private String safetyNight;
    private String polyline;
    @Column(name = "start_lat")
    private double startLat;
    @Column(name = "start_lng")
    private double startLng;
    @Column(name = "is_loop")
    private boolean loop = true;
    @Column(name = "rated_on")
    private LocalDate ratedOn = LocalDate.now();
    private boolean active = true;

    public CuratedWalk() { this.id = UUID.randomUUID(); }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal v) { this.distanceKm = v; }
    public int getEstMinutes() { return estMinutes; }
    public void setEstMinutes(int v) { this.estMinutes = v; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String v) { this.difficulty = v; }
    public String getSafetyDay() { return safetyDay; }
    public void setSafetyDay(String v) { this.safetyDay = v; }
    public String getSafetyNight() { return safetyNight; }
    public void setSafetyNight(String v) { this.safetyNight = v; }
    public String getPolyline() { return polyline; }
    public void setPolyline(String v) { this.polyline = v; }
    public double getStartLat() { return startLat; }
    public void setStartLat(double v) { this.startLat = v; }
    public double getStartLng() { return startLng; }
    public void setStartLng(double v) { this.startLng = v; }
    public boolean isLoop() { return loop; }
    public void setLoop(boolean v) { this.loop = v; }
    public LocalDate getRatedOn() { return ratedOn; }
    public void setRatedOn(LocalDate v) { this.ratedOn = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
