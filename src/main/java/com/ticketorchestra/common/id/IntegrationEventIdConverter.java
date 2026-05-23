package com.ticketorchestra.common.id;

public class IntegrationEventIdConverter extends BaseIdConverter<IntegrationEventId> {
    public IntegrationEventIdConverter() { super(IntegrationEventId::new, IntegrationEventId::id, IntegrationEventId.class); }
}
