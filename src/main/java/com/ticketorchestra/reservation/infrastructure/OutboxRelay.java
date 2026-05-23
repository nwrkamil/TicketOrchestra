package com.ticketorchestra.reservation.infrastructure;

import com.amazonaws.xray.entities.Subsegment;
import com.ticketorchestra.common.messaging.MessagingGateway;
import com.ticketorchestra.common.messaging.ReservationCreatedEvent;
import com.ticketorchestra.common.tracing.TracingHelper;
import com.ticketorchestra.reservation.domain.OutboxEvent;
import com.ticketorchestra.reservation.domain.OutboxStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final DynamoDbEnhancedClient enhancedClient;
    private final MessagingGateway messagingGateway;
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "MeterRegistry is a shared Spring bean")
    private final MeterRegistry meterRegistry;
    
    private final String instanceId = UUID.randomUUID().toString();
    private static final int LEASE_SECONDS = 30;
    private final AtomicLong pendingEventsCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        Gauge.builder("outbox.pending.events", pendingEventsCount, AtomicLong::get)
            .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 5000)
    public void relayEvents() {
        try {
            DynamoDbTable<OutboxEvent> table = getOutboxTable();
            DynamoDbIndex<OutboxEvent> statusIndex = table.index("StatusIndex");

            long pendingCount = 0;
            for (OutboxStatus status : List.of(OutboxStatus.NEW, OutboxStatus.FAILED)) {
                List<OutboxEvent> events = statusIndex.query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(status.name())))
                        .build())
                        .stream()
                        .flatMap(page -> page.items().stream())
                        .filter(e -> e.getNextRetryAt() == null || e.getNextRetryAt().isBefore(Instant.now()))
                        .toList();
                
                pendingCount += events.size();
                events.forEach(this::processEvent);
            }
            
            pendingEventsCount.set(pendingCount);
                
        } catch (ResourceNotFoundException e) {
            log.debug("Outbox table not yet initialized");
        } catch (Exception e) {
            log.error("Unexpected error in OutboxRelay: {}", e.getMessage(), e);
        }
    }

    private void processEvent(OutboxEvent event) {
        Subsegment subsegment = TracingHelper.beginSubsegment("RelayOutboxEvent");
        TracingHelper.putAnnotation(subsegment, "eventId", event.getEventId().toString());
        TracingHelper.putAnnotation(subsegment, "status", event.getStatus().name());
        
        if (!claimEvent(event)) {
            TracingHelper.endSubsegment(subsegment);
            return;
        }

        try {
            ReservationCreatedEvent payload = new ReservationCreatedEvent(
                    event.getEventId().id(),
                    event.getAggregateId().id()
            );

            messagingGateway.sendToReservationEvents(payload, "RESERVATION_CREATED");

            event.setStatus(OutboxStatus.SENT);
            event.setLeaseOwner(null);
            event.setLeaseExpiresAt(null);
            getOutboxTable().updateItem(event);
            
            TracingHelper.putAnnotation(subsegment, "result", "success");
            meterRegistry.counter("outbox.events.processed", "status", "SENT").increment();
            log.info("Successfully relayed event: {}", event.getEventId());
        } catch (Exception e) {
            TracingHelper.addException(subsegment, e);
            TracingHelper.putAnnotation(subsegment, "result", "failed");
            log.error("Error during relay of event {}: {}", event.getEventId(), e.getMessage());
            event.setStatus(OutboxStatus.FAILED);
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastErrorMessage(e.getMessage());
            event.setNextRetryAt(Instant.now().plusSeconds(30L * event.getRetryCount()));
            event.setLeaseOwner(null);
            event.setLeaseExpiresAt(null);
            getOutboxTable().updateItem(event);
            
            meterRegistry.counter("outbox.events.processed", "status", "FAILED").increment();
        } finally {
            TracingHelper.endSubsegment(subsegment);
        }
    }

    private boolean claimEvent(OutboxEvent event) {
        try {
            OutboxStatus originalStatus = event.getStatus();
            
            // Prepare the "claimed" state in the object before sending to DynamoDB
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLeaseOwner(instanceId);
            event.setLeaseExpiresAt(Instant.now().plusSeconds(LEASE_SECONDS));

            getOutboxTable().updateItem(r -> r.item(event)
                    .conditionExpression(Expression.builder()
                            .expression("eventId = :id AND #s = :expectedStatus AND (attribute_not_exists(leaseOwner) OR leaseOwner = :null OR leaseExpiresAt < :now)")
                            .expressionNames(Map.of("#s", "status"))
                            .expressionValues(Map.of(
                                    ":id", AttributeValue.builder().s(event.getEventId().toString()).build(),
                                    ":expectedStatus", AttributeValue.builder().s(originalStatus.name()).build(),
                                    ":null", AttributeValue.builder().nul(true).build(),
                                    ":now", AttributeValue.builder().s(Instant.now().toString()).build()
                            ))
                            .build()));
            
            return true;
        } catch (Exception e) {
            log.debug("Could not claim event {}: {}", event.getEventId(), e.getMessage());
            return false;
        }
    }

    private DynamoDbTable<OutboxEvent> getOutboxTable() {
        return enhancedClient.table("Outbox", TableSchema.fromBean(OutboxEvent.class));
    }
}
