package com.ticketorchestra.reservation.api;

import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
        String userId,
        UUID eventId,
        List<UUID> seatIds
) {
    public CreateReservationRequest {
        seatIds = seatIds != null ? List.copyOf(seatIds) : List.of();
    }
}
