package com.ticketorchestra.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull Instant dateTime,
        @NotNull UUID venueId,
        @NotNull @Min(1) @Max(100) @Schema(example = "5") Integer seatCount
) {
}
