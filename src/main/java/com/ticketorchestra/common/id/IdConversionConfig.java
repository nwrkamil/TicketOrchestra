package com.ticketorchestra.common.id;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IdConversionConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, EventId.class, EventId::from);
        registry.addConverter(String.class, SeatId.class, SeatId::from);
        registry.addConverter(String.class, ReservationId.class, ReservationId::from);
        registry.addConverter(String.class, PaymentId.class, PaymentId::from);
        registry.addConverter(String.class, IntegrationEventId.class, IntegrationEventId::from);
    }
}
