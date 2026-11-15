package com.irinajo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

    public record VoiceSettings(Boolean announcements, String speaker, Boolean calories, String music) {
        public static VoiceSettings defaults() { return new VoiceSettings(true, "ada", true, "duck"); }
    }

    public record ReminderSettings(Boolean daily, Integer minute, Boolean streak, Boolean weekly) {
        public static ReminderSettings defaults() { return new ReminderSettings(true, null, true, true); }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    private String name;
    private String bio;
    private String city;
    private String language = "en";

    @Column(name = "daily_goal_km")
    private BigDecimal dailyGoalKm = new BigDecimal("3.0");

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "weight_goal_kg_per_week")
    private BigDecimal weightGoalKgPerWeek;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> motivations = new ArrayList<>();

    private String region = "south";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "voice_settings")
    private VoiceSettings voiceSettings = VoiceSettings.defaults();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reminder_settings")
    private ReminderSettings reminderSettings = ReminderSettings.defaults();

    @Column(name = "share_anonymised_walks")
    private boolean shareAnonymisedWalks;

    @Column(name = "walk_safe_auto_after_dark")
    private boolean walkSafeAutoAfterDark;

    @Column(name = "avatar_key")
    private String avatarKey;

    @Column(name = "cover_key")
    private String coverKey;

    @Column(name = "updated_at")
    private long updatedAt;

    private long seq;

    protected Profile() {}

    public Profile(UUID userId) { this.userId = userId; }

    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public BigDecimal getDailyGoalKm() { return dailyGoalKm; }
    public void setDailyGoalKm(BigDecimal v) { this.dailyGoalKm = v; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal v) { this.weightKg = v; }
    public BigDecimal getWeightGoalKgPerWeek() { return weightGoalKgPerWeek; }
    public void setWeightGoalKgPerWeek(BigDecimal v) { this.weightGoalKgPerWeek = v; }
    public List<String> getMotivations() { return motivations; }
    public void setMotivations(List<String> m) { this.motivations = m; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public VoiceSettings getVoiceSettings() { return voiceSettings; }
    public void setVoiceSettings(VoiceSettings v) { this.voiceSettings = v; }
    public ReminderSettings getReminderSettings() { return reminderSettings; }
    public void setReminderSettings(ReminderSettings r) { this.reminderSettings = r; }
    public boolean isShareAnonymisedWalks() { return shareAnonymisedWalks; }
    public void setShareAnonymisedWalks(boolean b) { this.shareAnonymisedWalks = b; }
    public boolean isWalkSafeAutoAfterDark() { return walkSafeAutoAfterDark; }
    public void setWalkSafeAutoAfterDark(boolean b) { this.walkSafeAutoAfterDark = b; }
    public String getAvatarKey() { return avatarKey; }
    public void setAvatarKey(String k) { this.avatarKey = k; }
    public String getCoverKey() { return coverKey; }
    public void setCoverKey(String k) { this.coverKey = k; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long t) { this.updatedAt = t; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
}
