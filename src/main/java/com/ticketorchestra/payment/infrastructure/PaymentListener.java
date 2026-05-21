package com.ticketorchestra.payment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketorchestra.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ticketorchestra.payment.domain.PaymentService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentListener {

    @SuppressFBWarnings("EI2")
    @Lazy
    private final SqsClient sqsClient;
    private final PaymentService paymentService;
    @SuppressFBWarnings("EI2")
    private final ObjectMapper objectMapper;
    private String reservationEventsQueueUrl;
    private String paymentEventsQueueUrl;

    @Scheduled(fixedDelay = 2000)
    public void listen() {
        String resQueueUrl = getReservationEventsQueueUrl();
        String payQueueUrl = getPaymentEventsQueueUrl();
        
        if (resQueueUrl == null || payQueueUrl == null) {
            return;
        }

        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(resQueueUrl)
                .maxNumberOfMessages(5)
                .waitTimeSeconds(5)
                .messageAttributeNames("All")
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

        for (Message message : messages) {
            log.info("Received message: {}", message.body());

            try {
                IntegrationEvent incomingEvent = objectMapper.readValue(message.body(), IntegrationEvent.class);
                UUID reservationId = incomingEvent.reservationId();

                boolean success = paymentService.processPayment(reservationId, 100.0);

                String eventType = success ? "PAYMENT_COMPLETED" : "PAYMENT_FAILED";
                UUID paymentEventId = UUID.randomUUID();
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(payQueueUrl)
                        .messageBody(objectMapper.writeValueAsString(
                                IntegrationEvent.forReservation(paymentEventId, reservationId)))
                        .messageAttributes(java.util.Map.of(
                                "Type", MessageAttributeValue.builder()
                                        .dataType("String")
                                        .stringValue(eventType)
                                        .build(),
                                "IdempotencyKey", MessageAttributeValue.builder()
                                        .dataType("String")
                                        .stringValue(paymentEventId.toString())
                                        .build()))
                        .build());

                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(resQueueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());

                log.info("Payment event emitted: {} for reservation: {}", eventType, reservationId);
            } catch (RuntimeException e) {
                log.error("Payment domain processing failed for message {}. Message will remain in SQS.",
                        message.messageId(), e);
            } catch (Exception e) {
                log.error("Failed to parse or process reservation event message {}. Message will remain in SQS.",
                        message.messageId(), e);
            }
        }
    }

    private String getReservationEventsQueueUrl() {
        if (reservationEventsQueueUrl == null) {
            try {
                reservationEventsQueueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("reservation-events").build()).queueUrl();
            } catch (Exception e) {
                log.error("Failed to resolve reservation-events queue URL: {}", e.getMessage());
            }
        }
        return reservationEventsQueueUrl;
    }

    private String getPaymentEventsQueueUrl() {
        if (paymentEventsQueueUrl == null) {
            try {
                paymentEventsQueueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("payment-events").build()).queueUrl();
            } catch (Exception e) {
                log.error("Failed to resolve payment-events queue URL: {}", e.getMessage());
            }
        }
        return paymentEventsQueueUrl;
    }
}
