package com.gocommerce.orders.payment;

import java.math.BigDecimal;

public record PaymentChargeRequest(
        BigDecimal amount,
        String currency,
        String cardNumber,
        String description
) {}
