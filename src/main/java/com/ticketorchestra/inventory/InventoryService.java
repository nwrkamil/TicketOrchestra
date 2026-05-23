package com.ticketorchestra.inventory;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository repository;

    public void verifyEventAndSeats(EventId eventId, List<SeatId> seatIds) {
        if (repository.findEvent(eventId).isEmpty()) {
            throw new RuntimeException("Event not found");
        }
        for (SeatId seatId : seatIds) {
            Seat seat = repository.findSeat(eventId, seatId)
                    .orElseThrow(() -> new RuntimeException("Seat " + seatId + " not found for event " + eventId));
            if (!Objects.equals(seat.getEventId(), eventId.id())) {
                throw new RuntimeException("Seat " + seatId + " does not belong to event " + eventId);
            }
        }
    }

    public void unlockSeat(EventId eventId, SeatId seatId, ReservationId lockOwner) {
        Seat seat = repository.findSeat(eventId, seatId)
                .orElseThrow(() -> new RuntimeException("Seat not found"));

        if (seat.getStatus() == Seat.SeatStatus.AVAILABLE || seat.getStatus() == Seat.SeatStatus.SOLD) {
            return;
        }
        repository.unlockSeatIfOwned(eventId, seatId, lockOwner);
    }

    public void sellSeat(EventId eventId, SeatId seatId, ReservationId lockOwner) {
        Seat seat = repository.findSeat(eventId, seatId)
                .orElseThrow(() -> new RuntimeException("Seat not found"));

        if (seat.getStatus() == Seat.SeatStatus.SOLD) {
            return;
        }

        if (seat.getStatus() != Seat.SeatStatus.LOCKED) {
            throw new RuntimeException("Seat is not locked");
        }
        if (!Objects.equals(seat.getLockOwner(), lockOwner.id())) {
            throw new RuntimeException("Seat is locked by another reservation");
        }

        seat.setStatus(Seat.SeatStatus.SOLD);
        seat.setLockOwner(null);
        repository.saveSeat(seat);
    }
}
