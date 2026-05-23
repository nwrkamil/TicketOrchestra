package com.ticketorchestra.payment.domain;

import com.ticketorchestra.common.id.PaymentId;
import com.ticketorchestra.common.id.PaymentIdConverter;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.ReservationIdConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@DynamoDbBean
public class Payment {
    private PaymentId paymentId;
    private ReservationId reservationId;
    private double amount;
    private PaymentStatus status;
    private Instant createdAt;

    public Payment(PaymentId paymentId, ReservationId reservationId, double amount, PaymentStatus status) {
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    @DynamoDbPartitionKey
    @DynamoDbConvertedBy(PaymentIdConverter.class)
    public PaymentId getPaymentId() { return paymentId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "ReservationIndex")
    @DynamoDbConvertedBy(ReservationIdConverter.class)
    public ReservationId getReservationId() { return reservationId; }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED
    }
}
