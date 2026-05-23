package com.ticketorchestra.reservation.infrastructure;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.reservation.domain.OutboxEvent;
import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationCreationStore;
import com.ticketorchestra.reservation.domain.SeatUnavailableException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        EventId eventId = reservation.getEventId();
        ReservationId reservationId = reservation.getReservationId();

        for (SeatId seatId : reservation.getSeatIds()) {
            items.add(TransactWriteItem.builder()
                    .update(lockSeatUpdate(eventId, seatId, reservationId))
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

    private Update lockSeatUpdate(EventId eventId, SeatId seatId, ReservationId lockOwner) {
        return Update.builder()
                .tableName(SEATS_TABLE)
                .key(Map.of(
                        "eventId", stringValue(eventId.toString()),
                        "seatId", stringValue(seatId.toString())))
                .updateExpression("SET #status = :locked, lockOwner = :lockOwner")
                .conditionExpression("#status = :available")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":available", stringValue("AVAILABLE"),
                        ":locked", stringValue("LOCKED"),
                        ":lockOwner", stringValue(lockOwner.toString())))
                .build();
    }

    private AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
