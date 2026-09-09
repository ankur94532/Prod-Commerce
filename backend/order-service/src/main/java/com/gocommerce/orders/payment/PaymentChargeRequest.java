package com.gocommerce.orders.payment;

import java.math.BigDecimal;

/**
 * @param idempotencyKey stable for the life of an order. The charge call is retried on
 *        timeouts, and without this key each retry is a fresh charge against the customer.
 */
public record PaymentChargeRequest(
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        String cardNumber,
        String description
) {}
