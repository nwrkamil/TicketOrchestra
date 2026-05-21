package com.ticketorchestra.reservation.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ticketorchestra.inventory.InventoryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {
    
    private final ReservationRepository repository;
    private final InventoryService inventoryService;
    private final AntiFraudService antiFraudService;
    private final PricingService pricingService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Reservation createReservation(Reservation reservation) {
        reservation.setReservationId(UUID.randomUUID());
        reservation.setStatus(Reservation.ReservationStatus.PENDING);
        reservation.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));

        var antiFraudCheck = CompletableFuture.supplyAsync(
                () -> antiFraudService.check(reservation.getUserId()), executor);
        
        var seatLock = CompletableFuture.runAsync(
                () -> reservation.getSeatIds().forEach(seatId -> 
                    inventoryService.lockSeat(reservation.getEventId(), seatId)), executor);
        
        var pricing = CompletableFuture.supplyAsync(
                () -> pricingService.calculateTotal(reservation.getEventId(), reservation.getSeatIds()), executor);

        CompletableFuture.allOf(antiFraudCheck, seatLock, pricing).join();

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

        repository.saveWithOutbox(reservation, outboxEvent);
        return reservation;
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
