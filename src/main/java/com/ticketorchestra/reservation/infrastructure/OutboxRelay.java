package com.ticketorchestra.reservation.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ticketorchestra.reservation.domain.OutboxEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final DynamoDbEnhancedClient enhancedClient;
    @SuppressFBWarnings("EI2")
    @Lazy
    private final SqsClient sqsClient;
    private DynamoDbTable<OutboxEvent> outboxTable;
    private String queueUrl;

    private DynamoDbTable<OutboxEvent> getOutboxTable() {
        if (outboxTable == null) {
            outboxTable = enhancedClient.table("Outbox", TableSchema.fromBean(OutboxEvent.class));
        }
        return outboxTable;
    }

    @Scheduled(fixedDelay = 5000)
    public void relayEvents() {
        String qUrl = getQueueUrl();
        if (qUrl == null) {
            return;
        }

        List<OutboxEvent> unprocessedEvents = getOutboxTable().scan().items().stream()
                .filter(e -> !e.isProcessed())
                .toList();

        for (OutboxEvent event : unprocessedEvents) {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(qUrl)
                    .messageBody(event.getPayload())
                    .messageAttributes(java.util.Map.of(
                            "Type", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.getType())
                                    .build(),
                            "IdempotencyKey", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.getEventId().toString())
                                    .build()))
                    .build());
            
            event.setProcessed(true);
            getOutboxTable().updateItem(event);
            log.info("Relayed event: {}", event.getEventId());
        }
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
