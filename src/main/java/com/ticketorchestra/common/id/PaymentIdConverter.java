package com.ticketorchestra.common.id;

public class PaymentIdConverter extends BaseIdConverter<PaymentId> {
    public PaymentIdConverter() { super(PaymentId::new, PaymentId::id, PaymentId.class); }
}
