package com.ticketorchestra.inventory;

import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository repository;

    public void lockSeat(UUID eventId, UUID seatId) {
        Seat seat = repository.findSeat(eventId, seatId)
                .orElseThrow(() -> new RuntimeException("Seat not found"));
        
        if (seat.getStatus() != Seat.SeatStatus.AVAILABLE) {
            throw new RuntimeException("Seat is not available");
        }
        
        seat.setStatus(Seat.SeatStatus.LOCKED);
        repository.saveSeat(seat);
    }

    public void unlockSeat(UUID eventId, UUID seatId) {
        Seat seat = repository.findSeat(eventId, seatId)
                .orElseThrow(() -> new RuntimeException("Seat not found"));
        
        seat.setStatus(Seat.SeatStatus.AVAILABLE);
        repository.saveSeat(seat);
    }

    public void sellSeat(UUID eventId, UUID seatId) {
        Seat seat = repository.findSeat(eventId, seatId)
                .orElseThrow(() -> new RuntimeException("Seat not found"));

        if (seat.getStatus() == Seat.SeatStatus.SOLD) {
            return;
        }

        if (seat.getStatus() != Seat.SeatStatus.LOCKED) {
            throw new RuntimeException("Seat is not locked");
        }

        seat.setStatus(Seat.SeatStatus.SOLD);
        repository.saveSeat(seat);
    }
}
