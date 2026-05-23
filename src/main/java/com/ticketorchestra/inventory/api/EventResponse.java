package com.ticketorchestra.inventory.api;

import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.Seat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID eventId,
        String title,
        String description,
        Instant dateTime,
        UUID venueId,
        List<SeatResponse> seats
) {
    public EventResponse {
        seats = seats != null ? List.copyOf(seats) : List.of();
    }

    public static EventResponse from(Event event, List<Seat> seats) {
        return new EventResponse(
                event.getEventId(),
                event.getTitle(),
                event.getDescription(),
                event.getDateTime(),
                event.getVenueId(),
                seats.stream().map(SeatResponse::from).toList()
        );
    }

    public record SeatResponse(
            UUID seatId,
            double price,
            Seat.SeatStatus status
    ) {
        public static SeatResponse from(Seat seat) {
            return new SeatResponse(
                    seat.getSeatId(),
                    seat.getPrice(),
                    seat.getStatus()
            );
        }
    }
}
