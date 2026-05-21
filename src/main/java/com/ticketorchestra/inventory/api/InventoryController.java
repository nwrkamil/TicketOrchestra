package com.ticketorchestra.inventory.api;

import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryRepository repository;

    @PostMapping("/events")
    public void createEvent(@RequestBody Event event) {
        repository.saveEvent(event);
    }

    @PostMapping("/seats")
    public void createSeat(@RequestBody Seat seat) {
        seat.setStatus(Seat.SeatStatus.AVAILABLE);
        repository.saveSeat(seat);
    }

    @GetMapping("/events/{eventId}")
    public Event getEvent(@PathVariable UUID eventId) {
        return repository.findEvent(eventId).orElseThrow();
    }

    @GetMapping("/events/{eventId}/seats/{seatId}")
    public Seat getSeat(@PathVariable UUID eventId, @PathVariable UUID seatId) {
        return repository.findSeat(eventId, seatId).orElseThrow();
    }
}
