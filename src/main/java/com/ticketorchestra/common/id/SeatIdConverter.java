package com.ticketorchestra.common.id;

public class SeatIdConverter extends BaseIdConverter<SeatId> {
    public SeatIdConverter() { super(SeatId::new, SeatId::id, SeatId.class); }
}
