package com.ticketorchestra.payment.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    void save(Payment payment);
    Optional<Payment> findById(UUID paymentId);
    Optional<Payment> findByReservationId(UUID reservationId);
}
