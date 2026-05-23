package com.ticketorchestra.common.api;

public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super(message);
    }
}
