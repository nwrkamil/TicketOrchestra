package com.ticketorchestra.reservation.api;

import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The core module of the application, focusing on reservation orchestration, 
 * transaction consistency, and the Saga pattern implementation.
 */
@Tag(name = "Reservation", description = "Core module of the application. Focuses on reservation orchestration, distributed transactions, and Saga pattern.")
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
