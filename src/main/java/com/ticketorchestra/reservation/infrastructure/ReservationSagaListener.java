package com.ticketorchestra.reservation.infrastructure;

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
import java.util.UUID;

@Slf4j
@Component
public class ReservationSagaListener {

    private final SqsClient sqsClient;
    private final ReservationService reservationService;
    private String queueUrl;

    @SuppressFBWarnings("EI2")
    public ReservationSagaListener(@Lazy SqsClient sqsClient, ReservationService reservationService) {
        this.sqsClient = sqsClient;
        this.reservationService = reservationService;
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
            log.info("Processing message: {}", message.body());
            var attributes = message.messageAttributes();
            if (attributes == null || !attributes.containsKey("Type")) {
                log.warn("Received message without 'Type' attribute: {}. Attributes found: {}", message.body(), attributes);
                continue;
            }
            String type = attributes.get("Type").stringValue();
            String reservationIdStr = message.body().split(":")[1].replace("\"", "").replace("}", "").trim();
            UUID reservationId = UUID.fromString(reservationIdStr);

            if ("PAYMENT_COMPLETED".equals(type)) {
                reservationService.confirmReservation(reservationId);
            } else if ("PAYMENT_FAILED".equals(type)) {
                reservationService.cancelReservation(reservationId);
            }

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(qUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
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
