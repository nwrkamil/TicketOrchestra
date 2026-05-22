package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.reservation.domain.OutboxEvent;
import com.ticketorchestra.reservation.domain.OutboxStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final DynamoDbEnhancedClient enhancedClient;
    @SuppressFBWarnings("EI2")
    @Lazy
    private final SqsClient sqsClient;
    
    private final String instanceId = UUID.randomUUID().toString();
    private static final int LEASE_SECONDS = 30;
    private String queueUrl;

    @Scheduled(fixedDelay = 5000)
    public void relayEvents() {
        try {
            DynamoDbTable<OutboxEvent> table = getOutboxTable();
            DynamoDbIndex<OutboxEvent> statusIndex = table.index("StatusIndex");

            for (OutboxStatus status : List.of(OutboxStatus.NEW, OutboxStatus.FAILED)) {
                statusIndex.query(QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(status.name())))
                        .build())
                        .stream()
                        .flatMap(page -> page.items().stream())
                        .filter(e -> e.getNextRetryAt() == null || e.getNextRetryAt().isBefore(Instant.now()))
                        .forEach(this::processEvent);
            }
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            log.debug("Outbox table not yet initialized");
        } catch (Exception e) {
            log.error("Unexpected error in OutboxRelay: {}", e.getMessage(), e);
        }
    }

    private void processEvent(OutboxEvent event) {
        if (!claimEvent(event)) {
            return;
        }

        try {
            String qUrl = getQueueUrl();
            if (qUrl == null) throw new RuntimeException("Queue URL is null");
            
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(qUrl)
                    .messageBody(event.getPayload())
                    .messageAttributes(Map.of(
                            "Type", MessageAttributeValue.builder().dataType("String").stringValue(event.getType()).build(),
                            "IdempotencyKey", MessageAttributeValue.builder().dataType("String").stringValue(event.getEventId().toString()).build()))
                    .build());

            event.setStatus(OutboxStatus.SENT);
            event.setLeaseOwner(null);
            event.setLeaseExpiresAt(null);
            getOutboxTable().updateItem(event);
            log.info("Successfully relayed event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Error during relay of event {}: {}", event.getEventId(), e.getMessage());
            event.setStatus(OutboxStatus.FAILED);
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastErrorMessage(e.getMessage());
            event.setNextRetryAt(Instant.now().plusSeconds(30L * event.getRetryCount()));
            event.setLeaseOwner(null);
            event.setLeaseExpiresAt(null);
            getOutboxTable().updateItem(event);
        }
    }

    private boolean claimEvent(OutboxEvent event) {
        try {
            getOutboxTable().updateItem(r -> r.item(event)
                    .conditionExpression(Expression.builder()
                            .expression("eventId = :id AND #s = :expectedStatus AND (attribute_not_exists(leaseOwner) OR leaseOwner = :null OR leaseExpiresAt < :now)")
                            .expressionNames(Map.of("#s", "status"))
                            .expressionValues(Map.of(
                                    ":id", AttributeValue.builder().s(event.getEventId().toString()).build(),
                                    ":expectedStatus", AttributeValue.builder().s(event.getStatus().name()).build(),
                                    ":null", AttributeValue.builder().nul(true).build(),
                                    ":now", AttributeValue.builder().s(Instant.now().toString()).build()
                            ))
                            .build()));
            
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLeaseOwner(instanceId);
            event.setLeaseExpiresAt(Instant.now().plusSeconds(LEASE_SECONDS));
            getOutboxTable().updateItem(event);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private DynamoDbTable<OutboxEvent> getOutboxTable() {
        return enhancedClient.table("Outbox", TableSchema.fromBean(OutboxEvent.class));
    }

    private String getQueueUrl() {
        if (queueUrl == null) {
            try {
                queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("reservation-events").build()).queueUrl();
            } catch (Exception e) {
                log.error("Failed to resolve reservation-events queue URL in OutboxRelay: {}", e.getMessage());
            }
        }
        return queueUrl;
    }
}
