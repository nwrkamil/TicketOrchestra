package com.ticketorchestra.common.id;

import java.util.Objects;
import java.util.UUID;

public record ReservationId(UUID id) {
    public ReservationId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static ReservationId from(String id) {
        return new ReservationId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
