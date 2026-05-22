package com.ticketorchestra.common.id;

import java.util.Objects;
import java.util.UUID;

public record IntegrationEventId(UUID id) {
    public IntegrationEventId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static IntegrationEventId from(String id) {
        return new IntegrationEventId(UUID.fromString(id));
    }

    public static IntegrationEventId random() {
        return new IntegrationEventId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
