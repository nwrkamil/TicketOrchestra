package com.ticketorchestra.reservation;

import com.ticketorchestra.BaseIntegrationTest;
import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.common.messaging.PaymentStatusEvent;
import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationRepository;
import com.ticketorchestra.reservation.infrastructure.ReservationSagaListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class ReservationE2ETest extends BaseIntegrationTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSagaListener reservationSagaListener;

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    private String baseUrl;

    @BeforeEach
    void setUpBaseUrl() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void shouldCompleteReservationSaga() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        HttpClient httpClient = HttpClient.newHttpClient();

        // 1. Setup Event and Seat via Inventory API
        given().contentType(ContentType.JSON)
                .body("{\"eventId\": \"" + eventId + "\", \"title\": \"Test Event\", \"description\": \"Desc\", \"dateTime\": \"2026-05-20T10:00:00Z\", \"venueId\": \"" + UUID.randomUUID() + "\", \"status\": \"PUBLISHED\"}")
                .post(baseUrl + "/v1/inventory/events")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"eventId\": \"" + eventId + "\", \"seatId\": \"" + seatId + "\", \"price\": 100.0, \"status\": \"AVAILABLE\"}")
                .post(baseUrl + "/v1/inventory/seats")
                .then().statusCode(200);

        // 2. Trigger Reservation
        String reservationId = given().contentType(ContentType.JSON)
                .body("{\"userId\": \"test-user\", \"eventId\": \"" + eventId + "\", \"seatIds\": [\"" + seatId + "\"], \"totalPrice\": 0.0}")
                .post(baseUrl + "/v1/reservations")
                .then().statusCode(200)
                .extract().path("reservationId");

        // 3. Wait for Saga to process
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // 4. Verify Payment status
            HttpResponse<String> paymentResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/payments/reservation/" + reservationId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, paymentResponse.statusCode(), paymentResponse.body());
            
            // 5. Verify Seat status
            HttpResponse<String> seatResponse = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/inventory/events/" + eventId + "/seats/" + seatId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, seatResponse.statusCode(), seatResponse.body());
            assertTrue(seatResponse.body().contains("\"status\":\"SOLD\""), seatResponse.body());
        });
    }

    @Test
    void shouldNotLockAnySeatWhenOneOfManySeatsIsUnavailable() {
        UUID eventId = UUID.randomUUID();
        UUID availableSeatId = UUID.randomUUID();
        UUID unavailableSeatId = UUID.randomUUID();
        createEvent(new EventId(eventId));
        saveSeat(new EventId(eventId), new SeatId(availableSeatId), Seat.SeatStatus.AVAILABLE);
        saveSeat(new EventId(eventId), new SeatId(unavailableSeatId), Seat.SeatStatus.LOCKED);

        given().contentType(ContentType.JSON)
                .body("{\"userId\": \"test-user\", \"eventId\": \"" + eventId + "\", \"seatIds\": [\""
                        + availableSeatId + "\", \"" + unavailableSeatId + "\"], \"totalPrice\": 0.0}")
                .post(baseUrl + "/v1/reservations")
                .then().statusCode(500);

        assertEquals(Seat.SeatStatus.AVAILABLE,
                inventoryRepository.findSeat(new EventId(eventId), new SeatId(availableSeatId)).orElseThrow().getStatus());
        assertEquals(Seat.SeatStatus.LOCKED,
                inventoryRepository.findSeat(new EventId(eventId), new SeatId(unavailableSeatId)).orElseThrow().getStatus());
    }

    @Test
    void shouldNotLockSeatsWhenAntiFraudFails() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        createEvent(new EventId(eventId));
        saveSeat(new EventId(eventId), new SeatId(seatId), Seat.SeatStatus.AVAILABLE);

        given().contentType(ContentType.JSON)
                .body("{\"userId\": \"fraud-user\", \"eventId\": \"" + eventId + "\", \"seatIds\": [\""
                        + seatId + "\"], \"totalPrice\": 0.0}")
                .post(baseUrl + "/v1/reservations")
                .then().statusCode(500);

        assertEquals(Seat.SeatStatus.AVAILABLE,
                inventoryRepository.findSeat(new EventId(eventId), new SeatId(seatId)).orElseThrow().getStatus());
    }

    @Test
    void shouldHandleDuplicatePaymentCompletedEventIdempotently() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        createEvent(new EventId(eventId));
        saveLockedSeat(new EventId(eventId), new SeatId(seatId), new ReservationId(reservationId));
        saveReservation(new ReservationId(reservationId), new EventId(eventId), new SeatId(seatId), Reservation.ReservationStatus.PENDING);

        sendPaymentEventTwice("SUCCESS", new ReservationId(reservationId));
        
        await().untilAsserted(() -> {
            Reservation reservation = reservationRepository.findById(new ReservationId(reservationId)).orElseThrow();
            Seat seat = inventoryRepository.findSeat(new EventId(eventId), new SeatId(seatId)).orElseThrow();
            assertEquals(Reservation.ReservationStatus.PAID, reservation.getStatus());
            assertEquals(Seat.SeatStatus.SOLD, seat.getStatus());
        });
    }

    @Test
    void shouldHandleDuplicatePaymentFailedWithoutUnlockingSeatOwnedByNextReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID firstReservationId = UUID.randomUUID();
        UUID secondReservationId = UUID.randomUUID();
        createEvent(new EventId(eventId));
        saveLockedSeat(new EventId(eventId), new SeatId(seatId), new ReservationId(firstReservationId));
        saveReservation(new ReservationId(firstReservationId), new EventId(eventId), new SeatId(seatId), Reservation.ReservationStatus.PENDING);

        sendPaymentEvent("FAILED", new ReservationId(firstReservationId));
        
        await().untilAsserted(() -> {
            Reservation res = reservationRepository.findById(new ReservationId(firstReservationId)).orElseThrow();
            assertEquals(Reservation.ReservationStatus.CANCELLED, res.getStatus());
        });

        saveLockedSeat(new EventId(eventId), new SeatId(seatId), new ReservationId(secondReservationId));
        saveReservation(new ReservationId(secondReservationId), new EventId(eventId), new SeatId(seatId), Reservation.ReservationStatus.PENDING);

        await().untilAsserted(() -> {
            Seat seat = inventoryRepository.findSeat(new EventId(eventId), new SeatId(seatId)).orElseThrow();
            assertEquals(Seat.SeatStatus.LOCKED, seat.getStatus());
            assertEquals(secondReservationId, seat.getLockOwner());
        });

        sendPaymentEvent("FAILED", new ReservationId(firstReservationId));

        await().untilAsserted(() -> {
            Seat seat = inventoryRepository.findSeat(new EventId(eventId), new SeatId(seatId)).orElseThrow();
            assertEquals(Seat.SeatStatus.LOCKED, seat.getStatus());
            assertEquals(secondReservationId, seat.getLockOwner());
        });
    }

    private void createEvent(EventId eventId) {
        Event event = new Event();
        event.setEventId(eventId.id());
        event.setTitle("Test Event");
        event.setDescription("Desc");
        event.setDateTime(Instant.parse("2026-05-20T10:00:00Z"));
        event.setVenueId(UUID.randomUUID());
        event.setStatus(Event.EventStatus.PUBLISHED);
        inventoryRepository.saveEvent(event);
    }

    private void saveSeat(EventId eventId, SeatId seatId, Seat.SeatStatus status) {
        Seat seat = new Seat();
        seat.setEventId(eventId.id());
        seat.setSeatId(seatId.id());
        seat.setPrice(100.0);
        seat.setStatus(status);
        inventoryRepository.saveSeat(seat);
    }

    private void saveLockedSeat(EventId eventId, SeatId seatId, ReservationId lockOwner) {
        Seat seat = new Seat();
        seat.setEventId(eventId.id());
        seat.setSeatId(seatId.id());
        seat.setPrice(100.0);
        seat.setStatus(Seat.SeatStatus.LOCKED);
        seat.setLockOwner(lockOwner.id());
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName("Seats")
                .item(TableSchema.fromBean(Seat.class).itemToMap(seat, true))
                .build());
    }

    private void saveReservation(ReservationId reservationId,
                                 EventId eventId,
                                 SeatId seatId,
                                 Reservation.ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(reservationId.id());
        reservation.setUserId("test-user");
        reservation.setEventId(eventId.id());
        reservation.setSeatIds(List.of(seatId.id()));
        reservation.setTotalPrice(100.0);
        reservation.setStatus(status);
        reservation.setExpiresAt(Instant.now().plusSeconds(900));
        reservationRepository.save(reservation);
    }

    private void sendPaymentEventTwice(String status, ReservationId reservationId) {
        sendPaymentEvent(status, reservationId);
        sendPaymentEvent(status, reservationId);
    }

    private void sendPaymentEvent(String status, ReservationId reservationId) {
        PaymentStatusEvent event = new PaymentStatusEvent(
                UUID.randomUUID(),
                reservationId.id(),
                status
        );

        sqsTemplate.send(to -> to
                .queue("payment-events")
                .payload(event)
                .header("Type", "PAYMENT_STATUS"));
    }
}

