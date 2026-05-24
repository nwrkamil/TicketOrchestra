package com.ticketorchestra.reservation.domain;

import com.ticketorchestra.common.api.AntiFraudException;
import com.ticketorchestra.common.id.EventId;
import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.common.id.SeatId;
import com.ticketorchestra.inventory.InventoryService;
import com.ticketorchestra.reservation.api.CreateReservationRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "ObservationRegistry is a shared Spring bean")
    private final ObservationRegistry observationRegistry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Reservation createReservation(CreateReservationRequest request) {
        return Observation.createNotStarted("reservation.create", observationRegistry)
                .observe(() -> {
                    EventId eventId = new EventId(request.eventId());
                    List<SeatId> seatIds = seatIdsFrom(request);

                    reservationValidator.validate(eventId, seatIds);

                    Reservation reservation = Reservation.createPending(request.userId(), eventId, seatIds);
                    ReservationId reservationId = reservation.getReservationId();

                    var antiFraudCheck = CompletableFuture.supplyAsync(() ->
                            Observation.createNotStarted("antifraud.check", observationRegistry)
                                    .observe(() -> antiFraudService.check(reservation.getUserId())), executor);

                    var pricing = CompletableFuture.supplyAsync(() ->
                            Observation.createNotStarted("pricing.calculate", observationRegistry)
                                    .observe(() -> pricingService.calculateTotal(eventId, seatIds)), executor);

                    CompletableFuture.allOf(antiFraudCheck, pricing).join();

                    if (!antiFraudCheck.join()) {
                        throw new AntiFraudException("Anti-fraud check failed for user: " + reservation.getUserId());
                    }

                    reservation.setTotalPrice(pricing.join());

                    OutboxEvent outboxEvent = outboxEventService.createReservationCreatedEvent(reservationId);

                    reservationCreationStore.createWithSeatLocks(reservation, outboxEvent);

                    log.info("Reservation created successfully: {}", reservationId);
                    return reservation;
                });
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
        Observation.createNotStarted("reservation.confirm", observationRegistry)
                .observe(() -> {
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
                });
    }

    public void cancelReservation(ReservationId reservationId) {
        Observation.createNotStarted("reservation.cancel", observationRegistry)
                .observe(() -> {
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
                });
    }
}
