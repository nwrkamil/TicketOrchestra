package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.id.IntegrationEventId;
import com.ticketorchestra.common.id.IntegrationEventIdConverter;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.ReservationIdConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class OutboxEvent {
    private IntegrationEventId eventId;
    private ReservationId aggregateId;
    private String type;
    private String payload;
    private Instant createdAt;
    private OutboxStatus status;
    private String leaseOwner;
    private Instant leaseExpiresAt;
    private int retryCount;
    private String lastErrorMessage;
    private Instant nextRetryAt;

    public OutboxEvent(IntegrationEventId eventId, ReservationId aggregateId, String type, String payload) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.status = OutboxStatus.NEW;
        this.retryCount = 0;
    }

    public static OutboxEvent forReservationCreated(IntegrationEventId eventId, ReservationId reservationId, String payload) {
        return new OutboxEvent(eventId, reservationId, "RESERVATION_CREATED", payload);
    }

    @DynamoDbPartitionKey
    @DynamoDbConvertedBy(IntegrationEventIdConverter.class)
    public IntegrationEventId getEventId() { return eventId; }

    @DynamoDbSecondarySortKey(indexNames = "StatusIndex")
    public Instant getCreatedAt() { return createdAt; }

    @DynamoDbConvertedBy(ReservationIdConverter.class)
    public ReservationId getAggregateId() { return aggregateId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
    public OutboxStatus getStatus() { return status; }
}
