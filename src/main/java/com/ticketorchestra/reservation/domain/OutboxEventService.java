package com.ticketorchestra.reservation.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketorchestra.common.id.IntegrationEventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.messaging.IntegrationEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
    @SuppressFBWarnings("EI2")
    private final ObjectMapper objectMapper;


    public OutboxEvent createReservationCreatedEvent(ReservationId reservationId) {
        IntegrationEventId eventId = IntegrationEventId.random();
        IntegrationEvent event = IntegrationEvent.forReservation(eventId, reservationId);
        
        return OutboxEvent.forReservationCreated(
                eventId,
                reservationId,
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
