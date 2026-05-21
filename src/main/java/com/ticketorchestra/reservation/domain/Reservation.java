package com.ticketorchestra.reservation.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@DynamoDbBean
public class Reservation {
    private UUID reservationId;
    private String userId;
    private UUID eventId;
    private List<UUID> seatIds;
    private double totalPrice;
    private ReservationStatus status;
    private Instant expiresAt;

    public Reservation() {}

    public Reservation(UUID reservationId, String userId, UUID eventId, List<UUID> seatIds, double totalPrice, ReservationStatus status, Instant expiresAt) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.eventId = eventId;
        this.seatIds = new ArrayList<>(seatIds);
        this.totalPrice = totalPrice;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    @DynamoDbPartitionKey
    public UUID getReservationId() { return reservationId; }
    public void setReservationId(UUID reservationId) { this.reservationId = reservationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public List<UUID> getSeatIds() { return new ArrayList<>(seatIds); }
    public void setSeatIds(List<UUID> seatIds) { this.seatIds = new ArrayList<>(seatIds); }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public enum ReservationStatus {
        PENDING, PAID, CANCELLED
    }
}
