package com.ticketorchestra.reservation.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class Reservation {
    private UUID reservationId;
    private String userId;
    private UUID eventId;
    private List<UUID> seatIds;
    private double totalPrice;
    private ReservationStatus status;
    private Instant expiresAt;


    public List<UUID> getSeatIds() { return seatIds == null ? null : new ArrayList<>(seatIds); }

    public void setSeatIds(List<UUID> seatIds) { this.seatIds = seatIds == null ? null : new ArrayList<>(seatIds); }

    @DynamoDbPartitionKey
    public UUID getReservationId() { return reservationId; }

    public enum ReservationStatus {
        PENDING, PAID, CANCELLED
    }
}
