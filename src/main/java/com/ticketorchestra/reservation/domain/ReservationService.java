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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository repository;
    private final ReservationCreationStore reservationCreationStore;
    private final InventoryService inventoryService;
    private final OutboxEventService outboxEventService;
    private final ReservationValidator reservationValidator;
    private final AntiFraudService antiFraudService;
    private final PricingService pricingService;
    @SuppressFBWarnings("EI2")
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Reservation createReservation(CreateReservationRequest request) {
        EventId eventId = new EventId(request.eventId());
        List<SeatId> seatIds = seatIdsFrom(request);
        reservationValidator.validate(eventId, seatIds);

        Reservation reservation = Reservation.createPending(request.userId(), request.eventId(), request.seatIds());
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

        OutboxEvent outboxEvent = outboxEventService.createReservationCreatedEvent(reservationId);

        reservationCreationStore.createWithSeatLocks(reservation, outboxEvent);
        return reservation;
    }

    private List<SeatId> seatIdsFrom(CreateReservationRequest reservation) {
        if (reservation.seatIds() == null) {
            return List.of();
        }
        return reservation.seatIds().stream()
                .map(SeatId::new)
                .toList();
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
