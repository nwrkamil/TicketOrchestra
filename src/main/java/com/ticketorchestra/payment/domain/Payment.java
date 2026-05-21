package com.ticketorchestra.payment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class Payment {
    private UUID paymentId;
    private UUID reservationId;
    private double amount;
    private PaymentStatus status;
    private Instant createdAt;

    public Payment(UUID paymentId, UUID reservationId, double amount, PaymentStatus status) {
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    @DynamoDbPartitionKey
    public UUID getPaymentId() { return paymentId; }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED
    }
}
