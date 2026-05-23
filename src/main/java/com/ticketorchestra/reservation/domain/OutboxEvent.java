package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.id.IntegrationEventId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

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
    private OutboxStatus status;
    private String leaseOwner;
    private Instant leaseExpiresAt;
    private int retryCount;
    private String lastErrorMessage;
    private Instant nextRetryAt;

    public OutboxEvent(IntegrationEventId eventId, String aggregateId, String type, String payload) {
        this.eventId = eventId.id();
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.status = OutboxStatus.NEW;
        this.retryCount = 0;
    }

    public static OutboxEvent forReservationCreated(IntegrationEventId eventId, UUID reservationId, String payload) {
        return new OutboxEvent(eventId, reservationId.toString(), "RESERVATION_CREATED", payload);
    }

    @DynamoDbPartitionKey
    public UUID getEventId() { return eventId; }

    @DynamoDbSecondarySortKey(indexNames = "StatusIndex")
    public Instant getCreatedAt() { return createdAt; }

    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
    public OutboxStatus getStatus() { return status; }
}
