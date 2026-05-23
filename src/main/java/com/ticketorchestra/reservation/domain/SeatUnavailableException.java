package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.api.DomainException;

public class SeatUnavailableException extends DomainException {
    public SeatUnavailableException(String message, Throwable cause) {
        super(message);
    }
}
