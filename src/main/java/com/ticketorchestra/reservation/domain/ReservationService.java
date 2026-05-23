package com.ticketorchestra.reservation.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.IntegrationEventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.common.messaging.IntegrationEvent;
import com.ticketorchestra.inventory.InventoryService;
import com.ticketorchestra.reservation.api.CreateReservationRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {
    
    private final ReservationRepository repository;
    private final ReservationCreationStore reservationCreationStore;
    private final InventoryService inventoryService;
    private final AntiFraudService antiFraudService;
    private final PricingService pricingService;
    @SuppressFBWarnings("EI2")
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${ticketorchestra.reservations.max-seats:6}")
    private int maxSeatsPerReservation;

    public Reservation createReservation(CreateReservationRequest request) {
        Reservation reservation = new Reservation();
        reservation.setUserId(request.userId());
        reservation.setEventId(request.eventId());
        reservation.setSeatIds(request.seatIds());

        List<SeatId> seatIds = seatIdsFrom(reservation);
        validateSeatLimit(seatIds);

        reservation.setReservationId(UUID.randomUUID());
        reservation.setStatus(Reservation.ReservationStatus.PENDING);
        reservation.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        EventId eventId = new EventId(reservation.getEventId());
        ReservationId reservationId = new ReservationId(reservation.getReservationId());

        var antiFraudCheck = CompletableFuture.supplyAsync(
                () -> antiFraudService.check(reservation.getUserId()), executor);
        
        var pricing = CompletableFuture.supplyAsync(
                () -> pricingService.calculateTotal(eventId, seatIds), executor);

        CompletableFuture.allOf(antiFraudCheck, pricing).join();

        if (!antiFraudCheck.join()) {
            throw new RuntimeException("Anti-fraud check failed");
        }

        reservation.setTotalPrice(pricing.join());

        IntegrationEventId outboxEventId = IntegrationEventId.random();
        OutboxEvent outboxEvent = new OutboxEvent(
                outboxEventId,
                reservation.getReservationId().toString(),
                "RESERVATION_CREATED",
                toPayload(IntegrationEvent.forReservation(outboxEventId, reservationId))
        );

        reservationCreationStore.createWithSeatLocks(reservation, outboxEvent);
        return reservation;
    }

    private List<SeatId> seatIdsFrom(Reservation reservation) {
        if (reservation.getSeatIds() == null) {
            return List.of();
        }
        return reservation.getSeatIds().stream()
                .map(SeatId::new)
                .toList();
    }

    private void validateSeatLimit(List<SeatId> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new RuntimeException("At least one seat is required");
        }
        if (seatIds.size() > maxSeatsPerReservation) {
            throw new RuntimeException("Too many seats in one reservation");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new RuntimeException("Duplicate seats are not allowed");
        }
    }

    private String toPayload(IntegrationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize integration event", e);
        }
    }

    public void confirmReservation(ReservationId reservationId) {
        Reservation reservation = repository.findById(reservationId)
                .orElse(null);
        if (reservation == null) {
            log.warn("Ignoring payment completion for missing reservation: {}", reservationId);
            return;
        }

        if (reservation.getStatus() == Reservation.ReservationStatus.PAID) {
            log.info("Reservation already confirmed: {}", reservationId);
            return;
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED) {
            log.info("Ignoring payment completion for cancelled reservation: {}", reservationId);
            return;
        }

        reservation.setStatus(Reservation.ReservationStatus.PAID);
        repository.save(reservation);
        EventId eventId = new EventId(reservation.getEventId());
        reservation.getSeatIds().forEach(seatId ->
                inventoryService.sellSeat(eventId, new SeatId(seatId), reservationId));
        log.info("Reservation confirmed: {}", reservationId);
    }

    public void cancelReservation(ReservationId reservationId) {
        Reservation reservation = repository.findById(reservationId)
                .orElse(null);
        if (reservation == null) {
            log.warn("Ignoring payment failure for missing reservation: {}", reservationId);
            return;
        }

        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED) {
            log.info("Reservation already cancelled: {}", reservationId);
            return;
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.PAID) {
            log.info("Ignoring payment failure for paid reservation: {}", reservationId);
            return;
        }

        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
        repository.save(reservation);
        
        // Compensating action: unlock seats
        EventId eventId = new EventId(reservation.getEventId());
        reservation.getSeatIds().forEach(seatId -> 
                inventoryService.unlockSeat(eventId, new SeatId(seatId), reservationId));
        
        log.info("Reservation cancelled and seats unlocked: {}", reservationId);
    }
}
