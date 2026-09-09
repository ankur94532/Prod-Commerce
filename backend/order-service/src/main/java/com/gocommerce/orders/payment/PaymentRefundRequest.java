package com.gocommerce.orders.payment;

import java.math.BigDecimal;

/**
 * @param idempotencyKey stable per order, so a retried refund does not refund twice
 * @param transactionId  the charge being reversed
 */
public record PaymentRefundRequest(
        String idempotencyKey,
        String transactionId,
        BigDecimal amount,
        String currency,
        String reason
) {}
