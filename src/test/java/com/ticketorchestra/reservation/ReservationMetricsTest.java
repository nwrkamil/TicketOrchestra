package com.ticketorchestra.reservation;

import com.ticketorchestra.BaseIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class ReservationMetricsTest extends BaseIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void shouldRecordReservationCreatedMetrics() {
        // 1. Create an event with seats
        var createEventResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"title\":\"Metrics Test Event\",\"description\":\"Desc\",\"dateTime\":\"2026-05-20T10:00:00Z\",\"venueId\":\"%s\",\"seatCount\":1}"
                        .formatted(UUID.randomUUID()))
                .post(baseUrl + "/v1/inventory/events")
                .then()
                .statusCode(201)
                .extract();

        String eventId = createEventResponse.path("eventId");
        String seatId = createEventResponse.path("seatIds[0]");

        // 2. Trigger reservation
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":\"%s\",\"userId\":\"%s\",\"seatIds\":[\"%s\"]}"
                        .formatted(eventId, UUID.randomUUID(), seatId))
                .post(baseUrl + "/v1/reservations")
                .then()
                .statusCode(200);

        // 3. Verify metrics - Observation creates a timer named "reservation.create"
        assertThat(meterRegistry.find("reservation.create")
                .timer())
                .isNotNull();
    }

    @Test
    void shouldRecordFailedReservationMetrics() {
        // Trigger failed reservation (non-existent event)
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":\"%s\",\"userId\":\"%s\",\"seatIds\":[\"%s\"]}"
                        .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .post(baseUrl + "/v1/reservations")
                .then()
                .statusCode(404);

        // Verify failure metrics exist - Observation adds "error" tag on failure
        assertThat(meterRegistry.find("reservation.create")
                .tagKeys("error")
                .timers())
                .isNotEmpty();
    }

}
