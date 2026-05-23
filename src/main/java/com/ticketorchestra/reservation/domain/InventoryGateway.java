package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.SeatId;

import java.util.List;

public interface InventoryGateway {
    void verifyEventAndSeats(EventId eventId, List<SeatId> seatIds);
}
