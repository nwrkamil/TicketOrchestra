package com.ticketorchestra.inventory.domain;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {
    void saveSeat(Seat seat);
    Optional<Seat> findSeat(UUID eventId, UUID seatId);
    void saveEvent(Event event);
    Optional<Event> findEvent(UUID eventId);
}
