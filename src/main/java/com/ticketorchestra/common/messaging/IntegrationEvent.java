package com.ticketorchestra.common.messaging;

import com.ticketorchestra.common.id.IntegrationEventId;
import com.ticketorchestra.common.id.ReservationId;

import java.util.UUID;

public record IntegrationEvent(
        UUID eventId,
        String idempotencyKey,
        UUID reservationId
) {
    public static IntegrationEvent forReservation(IntegrationEventId eventId, ReservationId reservationId) {
        return new IntegrationEvent(eventId.id(), eventId.toString(), reservationId.id());
    }
}
