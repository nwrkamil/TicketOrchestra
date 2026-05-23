package com.ticketorchestra.common.id;

public class ReservationIdConverter extends BaseIdConverter<ReservationId> {
    public ReservationIdConverter() { super(ReservationId::new, ReservationId::id, ReservationId.class); }
}
