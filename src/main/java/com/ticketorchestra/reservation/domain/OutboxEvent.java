package com.ticketorchestra.reservation.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class OutboxEvent {
    private UUID eventId;
    private String aggregateId;
    private String type;
    private String payload;
    private Instant createdAt;
    private boolean processed;

    public OutboxEvent(UUID eventId, String aggregateId, String type, String payload) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.processed = false;
    }

    @DynamoDbPartitionKey
    public UUID getEventId() { return eventId; }
}
