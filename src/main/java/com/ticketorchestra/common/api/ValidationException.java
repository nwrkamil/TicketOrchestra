package com.ticketorchestra.common.api;

import org.springframework.http.HttpStatus;

public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
