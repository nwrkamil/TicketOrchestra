package com.ticketorchestra.reservation.api;

import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public Reservation create(@RequestBody Reservation reservation) {
        return service.createReservation(reservation);
    }
}
