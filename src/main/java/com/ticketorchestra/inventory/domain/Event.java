package com.ticketorchestra.inventory.domain;

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
public class Event {
    private UUID eventId;
    private String title;
    private String description;
    private Instant dateTime;
    private UUID venueId;
    private EventStatus status;

    @DynamoDbPartitionKey
    public UUID getEventId() { return eventId; }

    public enum EventStatus {
        DRAFT, PUBLISHED, CANCELLED
    }
}
