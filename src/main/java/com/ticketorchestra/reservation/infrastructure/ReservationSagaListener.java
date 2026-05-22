package com.ticketorchestra.reservation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.messaging.IntegrationEvent;
import lombok.extern.slf4j.Slf4j;
import com.ticketorchestra.reservation.domain.ReservationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Slf4j
@Component
public class ReservationSagaListener {

    private final SqsClient sqsClient;
    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;
    private String queueUrl;

    @SuppressFBWarnings("EI2")
    public ReservationSagaListener(@Lazy SqsClient sqsClient,
                                   ReservationService reservationService,
                                   ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.reservationService = reservationService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 2000)
    public void listen() {

        String qUrl = getQueueUrl();
        if (qUrl == null) {
            return;
        }

        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(qUrl)
                .maxNumberOfMessages(5)
                .waitTimeSeconds(5)
                .messageAttributeNames("All")
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

        for (Message message : messages) {
            // ... (processing logic)
            log.info("Processing message: {}", message.body());
            var attributes = message.messageAttributes();
            if (attributes == null || !attributes.containsKey("Type")) {
                log.warn("Received message without 'Type' attribute: {}. Attributes found: {}", message.body(), attributes);
                continue;
            }
            String type = attributes.get("Type").stringValue();
            try {
                IntegrationEvent event = objectMapper.readValue(message.body(), IntegrationEvent.class);
                ReservationId reservationId = new ReservationId(event.reservationId());
                if ("PAYMENT_COMPLETED".equals(type)) {
                    reservationService.confirmReservation(reservationId);
                } else if ("PAYMENT_FAILED".equals(type)) {
                    reservationService.cancelReservation(reservationId);
                } else {
                    log.warn("Received unsupported payment event type: {}", type);
                    continue;
                }

                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(qUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            } catch (RuntimeException e) {
                log.error("Domain processing failed for message {}. Message will remain in SQS.",
                        message.messageId(), e);
            } catch (Exception e) {
                log.error("Failed to parse or process message {}. Message will remain in SQS.",
                        message.messageId(), e);
            }
        }
    }

    private String getQueueUrl() {
        if (queueUrl == null) {
            try {
                queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName("payment-events").build()).queueUrl();
            } catch (Exception e) {
                log.error("Failed to resolve payment-events queue URL in ReservationSagaListener: {}", e.getMessage());
            }
        }
        return queueUrl;
    }
}
