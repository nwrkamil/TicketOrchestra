package com.ticketorchestra.reservation.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
        @NotBlank String userId,
        @NotNull UUID eventId,
        @NotEmpty List<UUID> seatIds
) {
    public CreateReservationRequest {
        seatIds = seatIds != null ? List.copyOf(seatIds) : List.of();
    }
}
