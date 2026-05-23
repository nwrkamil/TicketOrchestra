package com.ticketorchestra.common.messaging;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentStatusEvent(
    @NotNull UUID eventId,
    @NotNull UUID reservationId,
    @NotNull String status // SUCCESS, FAILED
) {}
