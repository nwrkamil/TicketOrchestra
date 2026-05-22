package com.ticketorchestra.common.id;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID id) {
    public EventId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static EventId from(String id) {
        return new EventId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
