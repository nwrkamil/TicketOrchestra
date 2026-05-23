package com.ticketorchestra.inventory.domain;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    void saveSeat(Seat seat);
    boolean unlockSeatIfOwned(EventId eventId, SeatId seatId, ReservationId lockOwner);
    Optional<Seat> findSeat(EventId eventId, SeatId seatId);
    List<Seat> findSeatsByEventId(EventId eventId);
    void saveEvent(Event event);
    Optional<Event> findEvent(EventId eventId);
    List<Event> findAllEvents();
}
