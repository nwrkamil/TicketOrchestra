package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.api.DomainException;
import org.springframework.http.HttpStatus;

public class SeatUnavailableException extends DomainException {
    public SeatUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.CONFLICT);
    }
}
