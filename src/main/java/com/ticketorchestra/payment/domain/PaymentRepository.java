package com.ticketorchestra.payment.domain;

import com.ticketorchestra.common.id.PaymentId;
import com.ticketorchestra.common.id.ReservationId;

import java.util.Optional;

public interface PaymentRepository {
    void save(Payment payment);
    Optional<Payment> findByReservationId(ReservationId reservationId);
}
