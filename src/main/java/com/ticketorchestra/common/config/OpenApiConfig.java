package com.ticketorchestra.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticketOrchestraOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("TicketOrchestra API")
                        .description("Distributed Ticket Reservation System")
                        .version("v1.0"));
    }
}
