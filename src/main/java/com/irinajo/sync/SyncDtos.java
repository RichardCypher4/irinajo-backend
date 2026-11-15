package com.irinajo.sync;

import com.irinajo.user.ProfileDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class SyncDtos {
    private SyncDtos() {}

    public record JourneyDto(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String id,
            Long startedAt,
            Integer durationSec,
            BigDecimal distanceKm,
            Integer steps,
            @Pattern(regexp = "^(morning|afternoon|evening|night)$") String timeOfDay,
            String area,
            List<String> landmarks,
            @Size(max = 40) String customName,
            @Size(max = 100_000) String polyline,
            @Size(max = 500) List<Integer> kmTimes,
            Boolean feltSafe,
            @NotNull Long updatedAt,
            boolean deleted
    ) {
        static JourneyDto of(Journey j) {
            if (j.isDeleted()) {
                return new JourneyDto(j.getId(), null, null, null, null, null, null, null, null, null, null, null,
                        j.getUpdatedAt(), true);
            }
            return new JourneyDto(j.getId(), j.getStartedAt(), j.getDurationSec(), j.getDistanceKm(), j.getSteps(),
                    j.getTimeOfDay(), j.getArea(), j.getLandmarks(), j.getCustomName(), j.getPolyline(),
                    j.getKmTimes(), j.getFeltSafe(), j.getUpdatedAt(), false);
        }
    }

    public record KeepsakeDto(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$") String id, @NotNull Long earnedAt) {}

    public record UploadBatch(@Valid @Size(max = 200) List<JourneyDto> journeys,
                              @Valid @Size(max = 200) List<KeepsakeDto> keepsakes) {}

    /** status: applied | duplicate | stale (a newer version is on the server; it comes back via /sync/changes). */
    public record ItemResult(String id, String status) {}

    public record UploadResult(List<ItemResult> journeys, int keepsakesAdded) {}

    public record Changes(ProfileDto profile, List<KeepsakeDto> keepsakes, List<JourneyDto> journeys,
                          long cursor, boolean hasMore) {}
}
