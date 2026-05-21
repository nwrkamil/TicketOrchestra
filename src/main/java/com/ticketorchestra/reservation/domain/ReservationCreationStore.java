package com.ticketorchestra.reservation.domain;

public interface ReservationCreationStore {
    void createWithSeatLocks(Reservation reservation, OutboxEvent outboxEvent);
}
