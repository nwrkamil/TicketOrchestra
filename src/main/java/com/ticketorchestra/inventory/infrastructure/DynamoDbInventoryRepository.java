package com.ticketorchestra.inventory.infrastructure;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;
import java.util.Optional;
import java.util.List;

@Repository
public class DynamoDbInventoryRepository implements InventoryRepository {

    private static final String SEATS_TABLE = "Seats";

    private final DynamoDbTable<Seat> seatTable;
    private final DynamoDbTable<Event> eventTable;
    private final DynamoDbClient dynamoDbClient;

    @SuppressFBWarnings("EI2")
    public DynamoDbInventoryRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.seatTable = enhancedClient.table(SEATS_TABLE, TableSchema.fromBean(Seat.class));
        this.eventTable = enhancedClient.table("Events", TableSchema.fromBean(Event.class));
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public void saveSeat(Seat seat) {
        seatTable.putItem(seat);
    }

    @Override
    public boolean unlockSeatIfOwned(EventId eventId, SeatId seatId, ReservationId lockOwner) {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(SEATS_TABLE)
                    .key(Map.of(
                            "eventId", stringValue(eventId.toString()),
                            "seatId", stringValue(seatId.toString())))
                    .updateExpression("SET #status = :available REMOVE lockOwner")
                    .conditionExpression("attribute_exists(eventId) AND attribute_exists(seatId) "
                            + "AND #status = :locked AND lockOwner = :lockOwner")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":available", stringValue(Seat.SeatStatus.AVAILABLE.name()),
                            ":locked", stringValue(Seat.SeatStatus.LOCKED.name()),
                            ":lockOwner", stringValue(lockOwner.toString())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public Optional<Seat> findSeat(EventId eventId, SeatId seatId) {
        return Optional.ofNullable(seatTable.getItem(Key.builder()
                .partitionValue(eventId.id().toString())
                .sortValue(seatId.id().toString())
                .build()));
    }

    @Override
    public void saveEvent(Event event) {
        eventTable.putItem(event);
    }

    @Override
    public Optional<Event> findEvent(EventId eventId) {
        return Optional.ofNullable(eventTable.getItem(Key.builder()
                .partitionValue(eventId.id().toString())
                .build()));
    }

    @Override
    public List<Event> findAllEvents() {
        return eventTable.scan().items().stream().toList();
    }

    private AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
