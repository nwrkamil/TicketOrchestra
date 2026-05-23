package com.ticketorchestra.common.messaging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqsQueues {
    public static final String RESERVATION_EVENTS = "reservation-events";
    public static final String PAYMENT_EVENTS = "payment-events";
}
