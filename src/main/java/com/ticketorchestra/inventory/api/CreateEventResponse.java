package com.ticketorchestra.inventory.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record CreateEventResponse(
        UUID eventId,
        UUID venueId,
        List<UUID> seatIds
) {
    public CreateEventResponse(UUID eventId, UUID venueId, List<UUID> seatIds) {
        this.eventId = eventId;
        this.venueId = venueId;
        this.seatIds = seatIds != null ? new ArrayList<>(seatIds) : null;
    }

    @Override
    public List<UUID> seatIds() {
        return seatIds != null ? Collections.unmodifiableList(seatIds) : null;
    }
}
