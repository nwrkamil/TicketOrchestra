package com.ticketorchestra.reservation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketorchestra.BaseIntegrationTest;
import com.ticketorchestra.common.messaging.IntegrationEvent;
import com.ticketorchestra.inventory.domain.Event;
import com.ticketorchestra.inventory.domain.InventoryRepository;
import com.ticketorchestra.inventory.domain.Seat;
import com.ticketorchestra.reservation.domain.Reservation;
import com.ticketorchestra.reservation.domain.ReservationRepository;
import com.ticketorchestra.reservation.infrastructure.ReservationSagaListener;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
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
    private SqsClient sqsClient;

    @Autowired
    private ObjectMapper objectMapper;

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
        Thread.sleep(8000);

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
    }

    @Test
    void shouldNotLockAnySeatWhenOneOfManySeatsIsUnavailable() {
        UUID eventId = UUID.randomUUID();
        UUID availableSeatId = UUID.randomUUID();
        UUID unavailableSeatId = UUID.randomUUID();
        createEvent(eventId);
        saveSeat(eventId, availableSeatId, Seat.SeatStatus.AVAILABLE);
        saveSeat(eventId, unavailableSeatId, Seat.SeatStatus.LOCKED);

        given().contentType(ContentType.JSON)
                .body("{\"userId\": \"test-user\", \"eventId\": \"" + eventId + "\", \"seatIds\": [\""
                        + availableSeatId + "\", \"" + unavailableSeatId + "\"], \"totalPrice\": 0.0}")
                .post(baseUrl + "/v1/reservations")
                .then().statusCode(500);

        assertEquals(Seat.SeatStatus.AVAILABLE,
                inventoryRepository.findSeat(eventId, availableSeatId).orElseThrow().getStatus());
        assertEquals(Seat.SeatStatus.LOCKED,
                inventoryRepository.findSeat(eventId, unavailableSeatId).orElseThrow().getStatus());
    }

    @Test
    void shouldNotLockSeatsWhenAntiFraudFails() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        createEvent(eventId);
        saveSeat(eventId, seatId, Seat.SeatStatus.AVAILABLE);

        given().contentType(ContentType.JSON)
                .body("{\"userId\": \"fraud-user\", \"eventId\": \"" + eventId + "\", \"seatIds\": [\""
                        + seatId + "\"], \"totalPrice\": 0.0}")
                .post(baseUrl + "/v1/reservations")
                .then().statusCode(500);

        assertEquals(Seat.SeatStatus.AVAILABLE,
                inventoryRepository.findSeat(eventId, seatId).orElseThrow().getStatus());
    }

    @Test
    void shouldHandleDuplicatePaymentCompletedEventIdempotently() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        createEvent(eventId);
        saveLockedSeat(eventId, seatId, reservationId);
        saveReservation(reservationId, eventId, seatId, Reservation.ReservationStatus.PENDING);

        sendPaymentEventTwice("PAYMENT_COMPLETED", reservationId);
        reservationSagaListener.listen();

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Seat seat = inventoryRepository.findSeat(eventId, seatId).orElseThrow();
        assertEquals(Reservation.ReservationStatus.PAID, reservation.getStatus());
        assertEquals(Seat.SeatStatus.SOLD, seat.getStatus());
    }

    @Test
    void shouldHandleDuplicatePaymentFailedWithoutUnlockingSeatOwnedByNextReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID firstReservationId = UUID.randomUUID();
        UUID secondReservationId = UUID.randomUUID();
        createEvent(eventId);
        saveLockedSeat(eventId, seatId, firstReservationId);
        saveReservation(firstReservationId, eventId, seatId, Reservation.ReservationStatus.PENDING);

        sendPaymentEvent("PAYMENT_FAILED", firstReservationId);
        reservationSagaListener.listen();

        saveLockedSeat(eventId, seatId, secondReservationId);
        saveReservation(secondReservationId, eventId, seatId, Reservation.ReservationStatus.PENDING);

        sendPaymentEvent("PAYMENT_FAILED", firstReservationId);
        reservationSagaListener.listen();

        Seat seat = inventoryRepository.findSeat(eventId, seatId).orElseThrow();
        assertEquals(Seat.SeatStatus.LOCKED, seat.getStatus());
        assertEquals(secondReservationId, seat.getLockOwner());
    }

    private void createEvent(UUID eventId) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setTitle("Test Event");
        event.setDescription("Desc");
        event.setDateTime(Instant.parse("2026-05-20T10:00:00Z"));
        event.setVenueId(UUID.randomUUID());
        event.setStatus(Event.EventStatus.PUBLISHED);
        inventoryRepository.saveEvent(event);
    }

    private void saveSeat(UUID eventId, UUID seatId, Seat.SeatStatus status) {
        Seat seat = new Seat();
        seat.setEventId(eventId);
        seat.setSeatId(seatId);
        seat.setPrice(100.0);
        seat.setStatus(status);
        inventoryRepository.saveSeat(seat);
    }

    private void saveLockedSeat(UUID eventId, UUID seatId, UUID lockOwner) {
        Seat seat = new Seat();
        seat.setEventId(eventId);
        seat.setSeatId(seatId);
        seat.setPrice(100.0);
        seat.setStatus(Seat.SeatStatus.LOCKED);
        seat.setLockOwner(lockOwner);
        inventoryRepository.saveSeat(seat);
    }

    private void saveReservation(UUID reservationId,
                                 UUID eventId,
                                 UUID seatId,
                                 Reservation.ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(reservationId);
        reservation.setUserId("test-user");
        reservation.setEventId(eventId);
        reservation.setSeatIds(List.of(seatId));
        reservation.setTotalPrice(100.0);
        reservation.setStatus(status);
        reservation.setExpiresAt(Instant.now().plusSeconds(900));
        reservationRepository.save(reservation);
    }

    private void sendPaymentEventTwice(String eventType, UUID reservationId) throws JsonProcessingException {
        sendPaymentEvent(eventType, reservationId);
        sendPaymentEvent(eventType, reservationId);
    }

    private void sendPaymentEvent(String eventType, UUID reservationId) throws JsonProcessingException {
        UUID eventId = UUID.randomUUID();
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName("payment-events")
                .build()).queueUrl();

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(objectMapper.writeValueAsString(IntegrationEvent.forReservation(eventId, reservationId)))
                .messageAttributes(Map.of(
                        "Type", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(eventType)
                                .build(),
                        "IdempotencyKey", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(eventId.toString())
                                .build()))
                .build());
    }
}
