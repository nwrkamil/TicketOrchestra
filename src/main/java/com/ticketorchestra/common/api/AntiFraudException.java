package com.ticketorchestra.common.api;

import org.springframework.http.HttpStatus;

public class AntiFraudException extends DomainException {
    public AntiFraudException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
