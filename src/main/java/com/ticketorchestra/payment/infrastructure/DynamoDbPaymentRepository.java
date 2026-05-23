package com.ticketorchestra.payment.infrastructure;

import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.payment.domain.Payment;
import com.ticketorchestra.payment.domain.PaymentRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

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
        return table.index("ReservationIndex")
                .query(QueryConditional.keyEqualTo(k -> k.partitionValue(reservationId.toString())))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
}
