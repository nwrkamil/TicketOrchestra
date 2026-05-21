package com.ticketorchestra.reservation.domain;

import org.springframework.stereotype.Service;

@Service
public class AntiFraudService {
    public boolean check(String userId) {
        // Mocked anti-fraud check
        return !"fraud-user".equals(userId);
    }
}
