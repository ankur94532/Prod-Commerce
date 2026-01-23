package com.gocommerce.orders.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MockStripePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockStripePaymentProvider.class);
    private static final String PROVIDER = "mock-stripe";

    @Override
    public PaymentResult charge(PaymentChargeRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentResult.failure(PROVIDER, "Invalid amount");
        }
        if (request.cardNumber() == null || request.cardNumber().isBlank()) {
            return PaymentResult.failure(PROVIDER, "Missing card number");
        }

        // Very simple “test cards” rule:
        //  - card ending with 0000 => DECLINED
        //  - anything else => SUCCESS
        String normalized = request.cardNumber().replaceAll("\\s+", "");
        boolean shouldFail = normalized.endsWith("0000");

        try {
            // simulate network latency / Stripe round-trip
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (shouldFail) {
            log.warn("MockStripe: failing payment for card ending with 0000");
            return PaymentResult.failure(PROVIDER, "Card declined (mock)");
        }

        String txId = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.info("MockStripe: payment succeeded, txId={}", txId);
        return PaymentResult.success(PROVIDER, txId);
    }
}
