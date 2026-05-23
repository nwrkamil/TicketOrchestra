package com.ticketorchestra.common.messaging;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.TraceHeader;
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
        try {
            var traceEntity = AWSXRay.getTraceEntity();
            if (traceEntity != null) {
                TraceHeader traceHeader = TraceHeader.fromEntity(traceEntity);
                sqsTemplate.send(to -> to
                        .queue(queueName)
                        .payload(payload)
                        .header(HEADER_NAME, type)
                        .header(TraceHeader.HEADER_KEY, traceHeader.toString()));
                return;
            }
        } catch (Exception e) {
            // X-Ray not available, continue without trace header
        }
        
        sqsTemplate.send(to -> to
                .queue(queueName)
                .payload(payload)
                .header(HEADER_NAME, type));
    }
}
