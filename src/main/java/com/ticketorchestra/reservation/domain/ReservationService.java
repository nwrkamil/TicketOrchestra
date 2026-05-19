package com.ticketorchestra.reservation.domain;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ReservationService {
    private final ReservationRepository repository;

    public ReservationService(ReservationRepository repository) {
        this.repository = repository;
    }

    public Reservation createReservation(Reservation reservation) {
        return repository.save(reservation);
    }
}
