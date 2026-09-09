package com.gocommerce.orders.payment;

import java.math.BigDecimal;

/**
 * @param idempotencyKey stable for the life of an order. The charge call is retried on
 *        timeouts, and without this key each retry is a fresh charge against the customer.
 * @param paymentToken   issued by the processor to the browser. This service never sees,
 *        transmits, or stores card data.
 */
public record PaymentChargeRequest(
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        String paymentToken,
        String description
) {}
