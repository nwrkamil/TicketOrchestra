package com.ticketorchestra.reservation.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class Reservation {
    //TODO move it to some configuration bean
    public static final int AMOUNT_TO_ADD = 15;
    private UUID reservationId;
    /**
     * In a production system, userId would be extracted from a JWT token.
     * It is included here as a field for demonstration purposes.
     */
    private String userId;
    private UUID eventId;
    private List<UUID> seatIds;
    private double totalPrice;
    private ReservationStatus status;
    private Instant expiresAt;

    public static Reservation createPending(String userId, UUID eventId, List<UUID> seatIds) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(UUID.randomUUID());
        reservation.setUserId(userId);
        reservation.setEventId(eventId);
        reservation.setSeatIds(seatIds);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setExpiresAt(Instant.now().plus(AMOUNT_TO_ADD, ChronoUnit.MINUTES));
        return reservation;
    }

    public List<UUID> getSeatIds() { return seatIds == null ? null : new ArrayList<>(seatIds); }

    public void setSeatIds(List<UUID> seatIds) { this.seatIds = seatIds == null ? null : new ArrayList<>(seatIds); }

    @DynamoDbPartitionKey
    public UUID getReservationId() { return reservationId; }

    public enum ReservationStatus {
        PENDING, PAID, CANCELLED
    }
}
