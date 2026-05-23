package com.ticketorchestra.reservation.domain;

import com.amazonaws.xray.entities.Subsegment;
import com.ticketorchestra.common.api.AntiFraudException;
import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.common.tracing.TracingHelper;
import com.ticketorchestra.inventory.InventoryService;
import com.ticketorchestra.reservation.api.CreateReservationRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final OutboxEventService outboxEventService;
    private final ReservationValidator reservationValidator;
    private final AntiFraudService antiFraudService;
    private final PricingService pricingService;
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "MeterRegistry is a shared Spring bean")
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Reservation createReservation(CreateReservationRequest request) {
        Subsegment subsegment = TracingHelper.beginSubsegment("CreateReservation");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            EventId eventId = new EventId(request.eventId());
            List<SeatId> seatIds = seatIdsFrom(request);
            
            TracingHelper.putMetadata(subsegment, "reservation", "userId", request.userId());
            TracingHelper.putMetadata(subsegment, "reservation", "eventId", request.eventId());
            TracingHelper.putMetadata(subsegment, "reservation", "seatCount", seatIds.size());
            
            reservationValidator.validate(eventId, seatIds);

            Reservation reservation = Reservation.createPending(request.userId(), eventId, seatIds);
            ReservationId reservationId = reservation.getReservationId();

            var antiFraudCheck = CompletableFuture.supplyAsync(() ->
                    TracingHelper.trace("AntiFraudCheck", () ->
                            antiFraudService.check(reservation.getUserId())), executor);
            
            var pricing = CompletableFuture.supplyAsync(() ->
                    TracingHelper.trace("PricingCalculation", () ->
                            pricingService.calculateTotal(eventId, seatIds)), executor);

            CompletableFuture.allOf(antiFraudCheck, pricing).join();

            if (!antiFraudCheck.join()) {
                throw new AntiFraudException("Anti-fraud check failed for user: " + reservation.getUserId());
            }

            reservation.setTotalPrice(pricing.join());

            OutboxEvent outboxEvent = outboxEventService.createReservationCreatedEvent(reservationId);

            reservationCreationStore.createWithSeatLocks(reservation, outboxEvent);
            
            TracingHelper.putAnnotation(subsegment, "reservationId", reservationId.toString());
            TracingHelper.putAnnotation(subsegment, "status", "success");
            
            meterRegistry.counter("reservation.created", "status", "success", "error", "none").increment();
            return reservation;
        } catch (Exception e) {
            TracingHelper.addException(subsegment, e);
            TracingHelper.putAnnotation(subsegment, "status", "failed");
            meterRegistry.counter("reservation.created", 
                "status", "failed", 
                "error", e.getClass().getSimpleName()).increment();
            throw e;
        } finally {
            TracingHelper.endSubsegment(subsegment);
            sample.stop(Timer.builder("reservation.creation.duration")
                .tag("status", "completed")
                .register(meterRegistry));
        }
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
        reservation.getSeatIds().forEach(seatId ->
                inventoryService.sellSeat(reservation.getEventId(), seatId, reservationId));
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
        reservation.getSeatIds().forEach(seatId -> 
                inventoryService.unlockSeat(reservation.getEventId(), seatId, reservationId));
        
        log.info("Reservation cancelled and seats unlocked: {}", reservationId);
    }
}
