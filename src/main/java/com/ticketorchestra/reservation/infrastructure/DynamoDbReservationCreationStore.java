package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.reservation.domain.OutboxEvent;
import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationCreationStore;
import com.ticketorchestra.reservation.domain.SeatUnavailableException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DynamoDbReservationCreationStore implements ReservationCreationStore {

    private static final String RESERVATIONS_TABLE = "Reservations";
    private static final String OUTBOX_TABLE = "Outbox";
    private static final String SEATS_TABLE = "Seats";
    private static final TableSchema<Reservation> RESERVATION_SCHEMA = TableSchema.fromBean(Reservation.class);
    private static final TableSchema<OutboxEvent> OUTBOX_SCHEMA = TableSchema.fromBean(OutboxEvent.class);

    private final DynamoDbClient dynamoDbClient;

    @SuppressFBWarnings(
            value = "EI2",
            justification = "DynamoDbClient is a Spring-managed AWS SDK client injected as an infrastructure dependency."
    )
    public DynamoDbReservationCreationStore(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public void createWithSeatLocks(Reservation reservation, OutboxEvent outboxEvent) {
        List<TransactWriteItem> items = new ArrayList<>();

        for (UUID seatId : reservation.getSeatIds()) {
            items.add(TransactWriteItem.builder()
                    .update(lockSeatUpdate(reservation.getEventId(), seatId, reservation.getReservationId()))
                    .build());
        }

        items.add(TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(RESERVATIONS_TABLE)
                        .item(RESERVATION_SCHEMA.itemToMap(reservation, true))
                        .conditionExpression("attribute_not_exists(reservationId)")
                        .build())
                .build());

        items.add(TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(OUTBOX_TABLE)
                        .item(OUTBOX_SCHEMA.itemToMap(outboxEvent, true))
                        .conditionExpression("attribute_not_exists(eventId)")
                        .build())
                .build());

        try {
            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(items)
                    .build());
        } catch (TransactionCanceledException e) {
            throw new SeatUnavailableException("One or more seats are not available", e);
        }
    }

    private Update lockSeatUpdate(UUID eventId, UUID seatId, UUID lockOwner) {
        return Update.builder()
                .tableName(SEATS_TABLE)
                .key(Map.of(
                        "eventId", stringValue(eventId),
                        "seatId", stringValue(seatId)))
                .updateExpression("SET #status = :locked, lockOwner = :lockOwner")
                .conditionExpression("#status = :available")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":available", stringValue("AVAILABLE"),
                        ":locked", stringValue("LOCKED"),
                        ":lockOwner", stringValue(lockOwner)))
                .build();
    }

    private AttributeValue stringValue(UUID value) {
        return AttributeValue.builder().s(value.toString()).build();
    }

    private AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
