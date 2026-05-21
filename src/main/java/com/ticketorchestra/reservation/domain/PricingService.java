package com.ticketorchestra.reservation.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PricingService {
    public double calculateTotal(UUID eventId, List<UUID> seatIds) {
        // Mocked dynamic pricing
        return seatIds.size() * 100.0;
    }
}
