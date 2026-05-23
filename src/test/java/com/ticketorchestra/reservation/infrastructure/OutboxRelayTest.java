package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.BaseIntegrationTest;
import com.ticketorchestra.common.id.IntegrationEventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.reservation.domain.OutboxEvent;
import com.ticketorchestra.reservation.domain.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class OutboxRelayTest extends BaseIntegrationTest {

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private DynamoDbEnhancedClient enhancedClient;

    @Test
    void shouldRelayNewEvents() {
        DynamoDbTable<OutboxEvent> table = enhancedClient.table("Outbox", TableSchema.fromBean(OutboxEvent.class));
        IntegrationEventId eventId = IntegrationEventId.random();
        UUID reservationId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, new ReservationId(reservationId), "TYPE", "{}");
        table.putItem(event);

        // Manually trigger or wait for scheduler
        outboxRelay.relayEvents();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEvent processed = table.getItem(k -> k.key(Key.builder().partitionValue(eventId.id().toString()).build()));
            if (processed != null) {
                log.info("Current event status: {}", processed.getStatus());
            } else {
                log.warn("Event not found in table");
            }
            assertEquals(OutboxStatus.SENT, processed != null ? processed.getStatus() : null);
        });
    }

    @Test
    void shouldRetryFailedEventsWithBackoff() {
        DynamoDbTable<OutboxEvent> table = enhancedClient.table("Outbox", TableSchema.fromBean(OutboxEvent.class));
        IntegrationEventId eventId = IntegrationEventId.random();
        UUID reservationId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, new ReservationId(reservationId), "FAIL", "{}");
        event.setStatus(OutboxStatus.FAILED);
        event.setRetryCount(1);
        event.setNextRetryAt(Instant.now().minusSeconds(1)); // Should be eligible for retry
        table.putItem(event);

        outboxRelay.relayEvents();
    }
}
