package com.gocommerce.orders.payment;

public record PaymentResult(
        boolean success,
        String provider,
        String transactionId,
        String failureReason
) {
    public static PaymentResult success(String provider, String transactionId) {
        return new PaymentResult(true, provider, transactionId, null);
    }

    public static PaymentResult failure(String provider, String failureReason) {
        return new PaymentResult(false, provider, null, failureReason);
    }
}
