package com.ticketorchestra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TicketOrchestraApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketOrchestraApplication.class, args);
    }
}
