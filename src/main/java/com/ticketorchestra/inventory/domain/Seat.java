package com.ticketorchestra.inventory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class Seat {
    private UUID eventId;
    private UUID seatId;
    private double price;
    private SeatStatus status;
    private Long version;

    @DynamoDbPartitionKey
    public UUID getEventId() { return eventId; }

    @DynamoDbSortKey
    public UUID getSeatId() { return seatId; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }

    public enum SeatStatus {
        AVAILABLE, LOCKED, SOLD
    }
}
