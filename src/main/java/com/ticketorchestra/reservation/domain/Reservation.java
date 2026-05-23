package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.EventIdConverter;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.ReservationIdConverter;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.common.id.SeatIdListConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
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
    private ReservationId reservationId;
    /**
     * In a production system, userId would be extracted from a JWT token.
     * It is included here as a field for demonstration purposes.
     */
    private String userId;
    private EventId eventId;
    private List<SeatId> seatIds;
    private double totalPrice;
    private ReservationStatus status;
    private Instant expiresAt;

    public static Reservation createPending(String userId, EventId eventId, List<SeatId> seatIds) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(new ReservationId(UUID.randomUUID()));
        reservation.setUserId(userId);
        reservation.setEventId(eventId);
        reservation.setSeatIds(seatIds);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setExpiresAt(Instant.now().plus(AMOUNT_TO_ADD, ChronoUnit.MINUTES));
        return reservation;
    }

    @DynamoDbPartitionKey
    @DynamoDbConvertedBy(ReservationIdConverter.class)
    public ReservationId getReservationId() { return reservationId; }

    @DynamoDbConvertedBy(EventIdConverter.class)
    public EventId getEventId() { return eventId; }

    @DynamoDbConvertedBy(SeatIdListConverter.class)
    public List<SeatId> getSeatIds() { return seatIds == null ? null : new ArrayList<>(seatIds); }

    public void setSeatIds(List<SeatId> seatIds) { this.seatIds = seatIds == null ? null : new ArrayList<>(seatIds); }

    public enum ReservationStatus {
        PENDING, PAID, CANCELLED
    }
}
