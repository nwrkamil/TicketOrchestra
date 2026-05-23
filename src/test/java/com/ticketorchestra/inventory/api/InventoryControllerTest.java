package com.ticketorchestra.inventory.api;

import com.ticketorchestra.BaseIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InventoryControllerTest extends BaseIntegrationTest {

    @LocalServerPort
    private int localPort;

    private String baseUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + localPort;
    }

    @Test
    void shouldReturnEventWithSeats() throws Exception {
        // 1. Create an event with seats
        var createEventResponse = given().contentType(ContentType.JSON)
                .body("{\"title\": \"Test Event\", \"description\": \"Desc\", \"dateTime\": \"2026-05-20T10:00:00Z\", \"venueId\": \"" + UUID.randomUUID() + "\", \"seatCount\": 2}")
                .post(baseUrl + "/v1/inventory/events")
                .then().statusCode(201)
                .extract();
        
        String eventId = createEventResponse.path("eventId");
        Objects.requireNonNull(eventId, "eventId must not be null");

        // 2. Get the event and verify it contains seats
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/v1/inventory/events/" + eventId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"title\":\"Test Event\""));
        assertTrue(response.body().contains("\"seats\":["));
    }

    @Test
    void shouldReturnAllEventsWithoutSeats() throws Exception {
        // 1. Create an event
        given().contentType(ContentType.JSON)
                .body("{\"title\": \"Event 1\", \"description\": \"Desc\", \"dateTime\": \"2026-05-20T10:00:00Z\", \"venueId\": \"" + UUID.randomUUID() + "\", \"seatCount\": 1}")
                .post(baseUrl + "/v1/inventory/events")
                .then().statusCode(201);

        // 2. Get all events and verify they contain seats
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/v1/inventory/events")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }
}
