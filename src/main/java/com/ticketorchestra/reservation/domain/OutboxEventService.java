package com.ticketorchestra.reservation.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketorchestra.common.id.IntegrationEventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
    private final ObjectMapper objectMapper;

    public OutboxEvent createReservationCreatedEvent(ReservationId reservationId) {
        IntegrationEventId eventId = IntegrationEventId.random();
        IntegrationEvent event = IntegrationEvent.forReservation(eventId, reservationId);
        
        return OutboxEvent.forReservationCreated(
                eventId,
                reservationId.id(),
                toPayload(event)
        );
    }

    private String toPayload(IntegrationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize integration event", e);
        }
    }
}
