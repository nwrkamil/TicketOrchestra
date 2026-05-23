package com.ticketorchestra.common.id;

public class EventIdConverter extends BaseIdConverter<EventId> {
    public EventIdConverter() { super(EventId::new, EventId::id, EventId.class); }
}
