package com.ticketorchestra.payment.infrastructure;

import com.ticketorchestra.common.id.PaymentId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.payment.domain.Payment;
import com.ticketorchestra.payment.domain.PaymentRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class DynamoDbPaymentRepository implements PaymentRepository {

    private final DynamoDbTable<Payment> table;

    public DynamoDbPaymentRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("Payments", TableSchema.fromBean(Payment.class));
    }

    @Override
    public void save(Payment payment) {
        table.putItem(payment);
    }

    @Override
    public Optional<Payment> findByReservationId(ReservationId reservationId) {
        // Simple scan for demo purposes, in production use GSI
        return table.scan().items().stream()
                .filter(p -> p.getReservationId().equals(reservationId.id()))
                .findFirst();
    }
}
