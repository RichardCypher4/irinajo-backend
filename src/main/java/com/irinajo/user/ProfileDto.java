package com.irinajo.user;

import java.math.BigDecimal;
import java.util.List;

public record ProfileDto(
        String name,
        String bio,
        String city,
        String language,
        BigDecimal dailyGoalKm,
        BigDecimal weightKg,
        BigDecimal weightGoalKgPerWeek,
        List<String> motivations,
        String region,
        Profile.VoiceSettings voice,
        Profile.ReminderSettings reminders,
        boolean shareAnonymisedWalks,
        boolean walkSafeAutoAfterDark,
        String avatarUrl,
        String coverUrl,
        long memberSince,
        long updatedAt
) {}
