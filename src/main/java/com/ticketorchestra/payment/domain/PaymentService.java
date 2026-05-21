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

        Payment existingPayment = repository.findById(reservationId).orElse(null);
        if (existingPayment != null) {
            return existingPayment.getStatus() == Payment.PaymentStatus.SUCCESS;
        }

        Payment payment = new Payment(reservationId, reservationId, amount, Payment.PaymentStatus.SUCCESS);
        repository.save(payment);
        
        return true;
    }
    
    public Payment getPaymentStatus(UUID reservationId) {
        return repository.findByReservationId(reservationId)
                .orElseThrow(() -> new RuntimeException("Payment not found for reservation: " + reservationId));
    }
}
