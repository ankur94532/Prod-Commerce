package com.gocommerce.orders.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A mock. It does not move money and it is not a payment integration.
 *
 * <p>What it does model faithfully is the part the order workflow depends on: it charges a
 * token rather than a card, it remembers idempotency keys so a retried charge returns the
 * original result instead of charging again, and a charge can be looked up after a lost
 * response.
 *
 * <p>Its memory is a map in this JVM. A real provider's record is shared and durable, so
 * this mock is weaker than the thing it stands in for in exactly one direction: with more
 * than one replica, two replicas do not share this map. Do not read a passing test here as
 * evidence that a real integration is idempotent — read it as evidence that the caller
 * holds up its side of the contract.
 */
@Service
public class MockStripePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockStripePaymentProvider.class);
    private static final String PROVIDER = "mock-stripe";

    private final Map<String, PaymentResult> charges = new ConcurrentHashMap<>();
    private final Map<String, PaymentResult> refunds = new ConcurrentHashMap<>();

    @Override
    @CircuitBreaker(name = "paymentProvider", fallbackMethod = "chargeFallback")
    @Retry(name = "paymentProvider")
    public PaymentResult charge(PaymentChargeRequest request) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            // Refusing is the safe answer: without a key a retry would charge again.
            return PaymentResult.failure(PROVIDER, "Missing idempotency key");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentResult.failure(PROVIDER, "Invalid amount");
        }
        if (request.paymentToken() == null || request.paymentToken().isBlank()) {
            return PaymentResult.failure(PROVIDER, "Missing payment token");
        }
        // Claiming the key must be atomic. Reading, deciding, then writing lets concurrent
        // retries of one order each pass the check and bill the customer several times.
        return charges.computeIfAbsent(request.idempotencyKey(), key -> settle(request));
    }

    private PaymentResult settle(PaymentChargeRequest request) {

        // The processor decides acceptance from the token; the card that produced it is
        // never visible here. A token minted from a declining test card carries the prefix.
        boolean declined = request.paymentToken().startsWith("pm_decline");

        try {
            Thread.sleep(200); // simulated round-trip
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (declined) {
            log.warn("MockStripe: declining token {}", request.paymentToken());
            // The decline is recorded too: replaying the key must not turn it into a charge.
            return PaymentResult.failure(PROVIDER, "Card declined (mock)");
        }
        String transactionId = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.info("MockStripe: charge succeeded, txId={}", transactionId);
        return PaymentResult.success(PROVIDER, transactionId);
    }

    @Override
    public Optional<PaymentResult> lookup(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(charges.get(idempotencyKey));
    }

    @Override
    @CircuitBreaker(name = "paymentProvider", fallbackMethod = "refundFallback")
    @Retry(name = "paymentProvider")
    public PaymentResult refund(PaymentRefundRequest request) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            return PaymentResult.failure(PROVIDER, "Missing idempotency key");
        }
        if (request.transactionId() == null || request.transactionId().isBlank()) {
            return PaymentResult.failure(PROVIDER, "Missing transaction to refund");
        }
        // Atomic for the same reason charging is: concurrent recovery passes must not
        // refund the same order twice.
        return refunds.computeIfAbsent(request.idempotencyKey(), key -> {
            String refundId = "re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            log.info("MockStripe: refunded {} as {} ({})", request.transactionId(), refundId, request.reason());
            return PaymentResult.success(PROVIDER, refundId);
        });
    }

    @SuppressWarnings("unused")
    private PaymentResult chargeFallback(PaymentChargeRequest request, Throwable ex) {
        log.warn("Payment charge fallback triggered; failing closed", ex);
        return PaymentResult.failure(PROVIDER, "Payment provider unavailable");
    }

    @SuppressWarnings("unused")
    private PaymentResult refundFallback(PaymentRefundRequest request, Throwable ex) {
        // A refund that did not happen must never look like one that did: the order stays
        // in compensation and is retried.
        log.error("Payment refund fallback triggered for transaction {}", request.transactionId(), ex);
        throw new PaymentProviderUnavailableException("Refund could not be confirmed", ex);
    }
}
