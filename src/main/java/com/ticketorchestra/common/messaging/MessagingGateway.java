package com.ticketorchestra.common.messaging;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagingGateway {

    @SuppressFBWarnings("EI2")
    private final SqsTemplate sqsTemplate;
    private static final String HEADER_NAME = "Type";

    public void sendToReservationEvents(ReservationCreatedEvent payload, String type) {
        send(SqsQueues.RESERVATION_EVENTS, payload, type);
    }

    public void sendToPaymentEvents(PaymentStatusEvent payload, String type) {
        send(SqsQueues.PAYMENT_EVENTS, payload, type);
    }

    private void send(String queueName, Object payload, String type) {
        sqsTemplate.send(to -> to
                .queue(queueName)
                .payload(payload)
                .header(HEADER_NAME, type));
    }
}
