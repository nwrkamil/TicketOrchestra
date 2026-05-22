package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.id.ReservationId;

import java.util.Optional;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(ReservationId reservationId);
}
