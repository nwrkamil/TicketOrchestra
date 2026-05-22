package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.messaging.PaymentStatusEvent;
import com.ticketorchestra.common.messaging.SqsQueues;
import com.ticketorchestra.reservation.domain.ReservationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationSagaListener {

    private final ReservationService reservationService;

    @SqsListener(SqsQueues.PAYMENT_EVENTS)
    public void onPaymentStatus(@Valid PaymentStatusEvent event) {
        log.info("Received payment status event: {}", event);
        
        ReservationId reservationId = new ReservationId(event.reservationId());
        
        if ("SUCCESS".equals(event.status())) {
            reservationService.confirmReservation(reservationId);
        } else if ("FAILED".equals(event.status())) {
            reservationService.cancelReservation(reservationId);
        } else {
            log.warn("Received unsupported payment status: {} for reservation {}", event.status(), reservationId);
        }
    }
}
