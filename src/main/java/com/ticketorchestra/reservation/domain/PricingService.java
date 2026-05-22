package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.SeatId;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PricingService {
    public double calculateTotal(EventId eventId, List<SeatId> seatIds) {
        // Mocked dynamic pricing
        return seatIds.size() * 100.0;
    }
}
