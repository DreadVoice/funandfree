package com.funandfree.events.event.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateEventRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @Size(max = 200) String venue,
        @NotNull Instant startsAtUtc,
        Instant endsAtUtc,
        @NotBlank @Size(max = 64) String timeZone,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Min(1) @Max(100000) int capacity,
        @Size(max = 2000) String imageUrl) {
}
