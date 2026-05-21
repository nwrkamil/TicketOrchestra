package com.ticketorchestra.payment.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final PaymentRepository repository;

    public boolean processPayment(UUID reservationId, double amount) {
        log.info("Processing payment for reservation: {} amount: {}", reservationId, amount);
        
        Payment payment = new Payment(UUID.randomUUID(), reservationId, amount, Payment.PaymentStatus.SUCCESS);
        repository.save(payment);
        
        return true;
    }
    
    public Payment getPaymentStatus(UUID reservationId) {
        return repository.findByReservationId(reservationId)
                .orElseThrow(() -> new RuntimeException("Payment not found for reservation: " + reservationId));
    }
}
