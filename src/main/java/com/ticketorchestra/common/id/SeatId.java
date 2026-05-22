package com.ticketorchestra.common.id;

import java.util.Objects;
import java.util.UUID;

public record SeatId(UUID id) {
    public SeatId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static SeatId from(String id) {
        return new SeatId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
