package com.ticketorchestra;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithTest {

    @Test
    void verifyModularArchitecture() {
        ApplicationModules modules = ApplicationModules.of(TicketOrchestraApplication.class);
        modules.forEach(System.out::println);
        modules.verify();
    }
}
