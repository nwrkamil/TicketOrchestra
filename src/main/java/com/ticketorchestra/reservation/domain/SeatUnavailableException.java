package com.ticketorchestra.reservation.domain;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
