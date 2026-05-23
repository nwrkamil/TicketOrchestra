package com.ticketorchestra.reservation.api;

import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.reservation.domain.Reservation;

public record ReservationResponse(ReservationId reservationId, String userId, String status) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getUserId(),
                reservation.getStatus().name()
        );
    }
}
