package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DynamoDbReservationRepository implements ReservationRepository {

    private final DynamoDbTable<Reservation> table;

    public DynamoDbReservationRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("Reservations", TableSchema.fromBean(Reservation.class));
    }

    @Override
    public Reservation save(Reservation reservation) {
        table.putItem(reservation);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(UUID reservationId) {
        return Optional.ofNullable(table.getItem(r -> r.key(k -> k.partitionValue(reservationId.toString()))));
    }
}
