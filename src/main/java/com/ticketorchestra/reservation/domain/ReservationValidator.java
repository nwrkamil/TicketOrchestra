package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.SeatId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationValidator {
    private final InventoryGateway inventoryGateway;

    @Value("${ticketorchestra.reservations.max-seats:6}")
    private int maxSeatsPerReservation;

    public void validate(EventId eventId, List<SeatId> seatIds) {
        validateSeatLimit(seatIds);
        inventoryGateway.verifyEventAndSeats(eventId, seatIds);
    }

    private void validateSeatLimit(List<SeatId> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new RuntimeException("At least one seat is required");
        }
        if (seatIds.size() > maxSeatsPerReservation) {
            throw new RuntimeException("Too many seats in one reservation");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new RuntimeException("Duplicate seats are not allowed");
        }
    }
}
