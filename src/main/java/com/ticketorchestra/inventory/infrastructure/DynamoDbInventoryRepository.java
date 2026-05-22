package com.ticketorchestra.inventory.infrastructure;

import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class DynamoDbInventoryRepository implements InventoryRepository {

    private final DynamoDbTable<Seat> seatTable;
    private final DynamoDbTable<Event> eventTable;

    public DynamoDbInventoryRepository(DynamoDbEnhancedClient enhancedClient) {
        this.seatTable = enhancedClient.table("Seats", TableSchema.fromBean(Seat.class));
        this.eventTable = enhancedClient.table("Events", TableSchema.fromBean(Event.class));
    }

    @Override
    public void saveSeat(Seat seat) {
        seatTable.putItem(seat);
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
}
