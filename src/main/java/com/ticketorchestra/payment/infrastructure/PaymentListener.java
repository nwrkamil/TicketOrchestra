package com.ticketorchestra.payment.infrastructure;

import com.ticketorchestra.common.id.IntegrationEventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.messaging.MessagingGateway;
import com.ticketorchestra.common.messaging.PaymentStatusEvent;
import com.ticketorchestra.common.messaging.ReservationCreatedEvent;
import com.ticketorchestra.common.messaging.SqsQueues;
import com.ticketorchestra.payment.domain.PaymentService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;
    private final MessagingGateway messagingGateway;

    @SqsListener(SqsQueues.RESERVATION_EVENTS)
    public void onReservationCreated(@Valid ReservationCreatedEvent event) {
        log.info("Received reservation created event: {}", event);
        
        ReservationId reservationId = new ReservationId(event.reservationId());
        boolean success = paymentService.processPayment(reservationId, 100.0);

        String status = success ? "SUCCESS" : "FAILED";
        IntegrationEventId paymentEventId = IntegrationEventId.random();
        
        PaymentStatusEvent responseEvent = new PaymentStatusEvent(
                paymentEventId.id(),
                reservationId.id(),
                status
        );

        messagingGateway.sendToPaymentEvents(responseEvent, "PAYMENT_STATUS");

        log.info("Payment event emitted: {} for reservation: {}", status, reservationId);
    }
}
