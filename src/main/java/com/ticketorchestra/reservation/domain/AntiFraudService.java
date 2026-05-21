package com.ticketorchestra.reservation.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AntiFraudService {
    public boolean check(String userId) {
        // Mocked anti-fraud check
        return true;
    }
}
