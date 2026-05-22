package com.ticketorchestra.payment.api;

import com.ticketorchestra.common.id.ReservationId;
import com.ticketorchestra.payment.domain.Payment;
import com.ticketorchestra.payment.domain.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This controller and the payment module represent a simplified abstraction.
 * In a production system, this would integrate with external payment providers (e.g., Stripe, PayPal).
 */
@Tag(name = "Payment", description = "Simplified payment abstraction. In a production system, this would integrate with external payment providers.")
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/reservation/{reservationId}")
    public Payment getPaymentStatus(@PathVariable ReservationId reservationId) {
        return paymentService.getPaymentStatus(reservationId);
    }
}
