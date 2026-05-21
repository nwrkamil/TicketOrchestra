package com.ticketorchestra.payment.api;

import com.ticketorchestra.payment.domain.Payment;
import com.ticketorchestra.payment.domain.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/reservation/{reservationId}")
    public Payment getPaymentStatus(@PathVariable UUID reservationId) {
        return paymentService.getPaymentStatus(reservationId);
    }
}
