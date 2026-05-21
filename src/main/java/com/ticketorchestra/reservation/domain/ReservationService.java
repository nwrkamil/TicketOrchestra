package com.ticketorchestra.reservation.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ticketorchestra.inventory.InventoryService;
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
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${ticketorchestra.reservations.max-seats:6}")
    private int maxSeatsPerReservation;

    public Reservation createReservation(Reservation reservation) {
        validateSeatLimit(reservation.getSeatIds());

        reservation.setReservationId(UUID.randomUUID());
        reservation.setStatus(Reservation.ReservationStatus.PENDING);
        reservation.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));

        var antiFraudCheck = CompletableFuture.supplyAsync(
                () -> antiFraudService.check(reservation.getUserId()), executor);
        
        var pricing = CompletableFuture.supplyAsync(
                () -> pricingService.calculateTotal(reservation.getEventId(), reservation.getSeatIds()), executor);

        CompletableFuture.allOf(antiFraudCheck, pricing).join();

        if (!antiFraudCheck.join()) {
            throw new RuntimeException("Anti-fraud check failed");
        }

        reservation.setTotalPrice(pricing.join());

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                reservation.getReservationId().toString(),
                "RESERVATION_CREATED",
                String.format("{\"reservationId\":\"%s\"}", reservation.getReservationId())
        );

        reservationCreationStore.createWithSeatLocks(reservation, outboxEvent);
        return reservation;
    }

    private void validateSeatLimit(List<UUID> seatIds) {
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

    public void confirmReservation(UUID reservationId) {
        Reservation reservation = repository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        reservation.setStatus(Reservation.ReservationStatus.PAID);
        repository.save(reservation);
        reservation.getSeatIds().forEach(seatId ->
                inventoryService.sellSeat(reservation.getEventId(), seatId));
        log.info("Reservation confirmed: {}", reservationId);
    }

    public void cancelReservation(UUID reservationId) {
        Reservation reservation = repository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);
        repository.save(reservation);
        
        // Compensating action: unlock seats
        reservation.getSeatIds().forEach(seatId -> 
                inventoryService.unlockSeat(reservation.getEventId(), seatId));
        
        log.info("Reservation cancelled and seats unlocked: {}", reservationId);
    }
}
