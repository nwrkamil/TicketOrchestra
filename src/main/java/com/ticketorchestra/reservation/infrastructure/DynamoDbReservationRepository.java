package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.reservation.domain.OutboxEvent;
import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DynamoDbReservationRepository implements ReservationRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Reservation> reservationTable;
    private final DynamoDbTable<OutboxEvent> outboxTable;

    public DynamoDbReservationRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.reservationTable = enhancedClient.table("Reservations", TableSchema.fromBean(Reservation.class));
        this.outboxTable = enhancedClient.table("Outbox", TableSchema.fromBean(OutboxEvent.class));
    }

    @Override
    public Reservation save(Reservation reservation) {
        reservationTable.putItem(reservation);
        return reservation;
    }

    @Override
    public void saveWithOutbox(Reservation reservation, OutboxEvent outboxEvent) {
        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(reservationTable, reservation)
                .addPutItem(outboxTable, outboxEvent)
                .build();
        enhancedClient.transactWriteItems(request);
    }

    @Override
    public Optional<Reservation> findById(UUID reservationId) {
        return Optional.ofNullable(reservationTable.getItem(r -> r.key(k -> k.partitionValue(reservationId.toString()))));
    }
}
