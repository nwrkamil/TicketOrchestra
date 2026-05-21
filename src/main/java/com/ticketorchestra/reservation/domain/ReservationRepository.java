package com.ticketorchestra.reservation.domain;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    void saveWithOutbox(Reservation reservation, OutboxEvent outboxEvent);
    Optional<Reservation> findById(UUID reservationId);
}
