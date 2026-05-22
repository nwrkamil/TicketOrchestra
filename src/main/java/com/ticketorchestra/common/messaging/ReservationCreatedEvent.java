package com.ticketorchestra.common.messaging;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReservationCreatedEvent(
    @NotNull UUID eventId,
    @NotNull UUID reservationId
) {}
