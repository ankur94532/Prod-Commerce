package com.gocommerce.orders.payment;

public interface PaymentProvider {
    PaymentResult charge(PaymentChargeRequest request);
}
