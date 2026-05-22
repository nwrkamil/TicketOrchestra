package com.ticketorchestra.inventory.api;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * This controller and the inventory module represent a simplified abstraction.
 * The primary focus of this project is on the reservation process and saga orchestration.
 */
@Tag(name = "Inventory", description = "Simplified inventory abstraction. Note: The primary focus of this project is the reservation process and saga orchestration.")
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
    public Event getEvent(@PathVariable EventId eventId) {
        return repository.findEvent(eventId).orElseThrow();
    }

    @GetMapping("/events/{eventId}/seats/{seatId}")
    public Seat getSeat(@PathVariable EventId eventId, @PathVariable SeatId seatId) {
        return repository.findSeat(eventId, seatId).orElseThrow();
    }
}
