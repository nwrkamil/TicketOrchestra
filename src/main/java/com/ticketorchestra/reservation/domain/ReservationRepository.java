package com.ticketorchestra.reservation.domain;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(UUID reservationId);
}
