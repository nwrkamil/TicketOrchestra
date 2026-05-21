package com.ticketorchestra.common.messaging;

import java.util.UUID;

public record IntegrationEvent(
        UUID eventId,
        String idempotencyKey,
        UUID reservationId
) {
    public static IntegrationEvent forReservation(UUID eventId, UUID reservationId) {
        return new IntegrationEvent(eventId, eventId.toString(), reservationId);
    }
}
