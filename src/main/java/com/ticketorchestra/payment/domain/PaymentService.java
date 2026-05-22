package com.ticketorchestra.payment.domain;

import com.ticketorchestra.common.id.PaymentId;
import com.ticketorchestra.common.id.ReservationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final PaymentRepository paymentRepository;

    public boolean processPayment(ReservationId reservationId, double amount) {
        log.info("Processing payment for reservation: {} amount: {}", reservationId, amount);

        //simple protection against more than one payment for the same reservation
        return paymentRepository.findByReservationId(reservationId)
                .map(payment -> payment.getStatus() == Payment.PaymentStatus.SUCCESS)
                .orElseGet(() -> someSophisticatedPaymentService(reservationId, amount));
    }

    private boolean someSophisticatedPaymentService(ReservationId reservationId, double amount) {
        PaymentId paymentId = new PaymentId(UUID.randomUUID());
        Payment payment = new Payment(paymentId, reservationId, amount, Payment.PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        return true;
    }

    public Payment getPaymentStatus(ReservationId reservationId) {
        return paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new RuntimeException("Payment not found for reservation: " + reservationId));
    }
}
