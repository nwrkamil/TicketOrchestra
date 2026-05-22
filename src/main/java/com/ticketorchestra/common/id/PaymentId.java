package com.ticketorchestra.common.id;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID id) {
    public PaymentId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static PaymentId from(String id) {
        return new PaymentId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
