package com.ticketorchestra.inventory.api;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setEventId(eventId);
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setDateTime(request.dateTime());
        event.setVenueId(request.venueId());
        repository.saveEvent(event);

        List<UUID> seatIds = new ArrayList<>(request.seatCount());
        for (int i = 0; i < request.seatCount(); i++) {
            UUID seatId = UUID.randomUUID();
            Seat seat = new Seat();
            seat.setEventId(eventId);
            seat.setSeatId(seatId);
            seat.setPrice(100.0);
            seat.setStatus(Seat.SeatStatus.AVAILABLE);
            repository.saveSeat(seat);
            seatIds.add(seatId);
        }

        return new CreateEventResponse(eventId, request.venueId(), List.copyOf(seatIds));
    }

    @GetMapping("/events/{eventId}")
    public EventResponse getEvent(@PathVariable EventId eventId) {
        Event event = repository.findEvent(eventId).orElseThrow();
        List<Seat> seats = repository.findSeatsByEventId(eventId);
        return EventResponse.from(event, seats);
    }

    @GetMapping("/events")
    public List<EventResponse> getEvents() {
        return repository.findAllEvents().stream()
                .map(event -> EventResponse.from(event, List.of()))
                .toList();
    }

    @GetMapping("/events/{eventId}/seats/{seatId}")
    public Seat getSeat(@PathVariable EventId eventId, @PathVariable SeatId seatId) {
        return repository.findSeat(eventId, seatId).orElseThrow();
    }
}
