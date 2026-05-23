package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.inventory.InventoryService;
import com.ticketorchestra.reservation.domain.InventoryGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class InventoryServiceAdapter implements InventoryGateway {
    private final InventoryService inventoryService;

    @Override
    public void verifyEventAndSeats(EventId eventId, List<SeatId> seatIds) {
        inventoryService.verifyEventAndSeats(eventId, seatIds);
    }
}
