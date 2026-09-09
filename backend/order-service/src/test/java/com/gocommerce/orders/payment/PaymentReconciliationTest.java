package com.gocommerce.orders.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The provider contract the checkout workflow depends on. The charge call sits behind a
 * retry policy, so a provider that treats a repeated key as a new charge would bill the
 * customer twice for one order.
 */
class PaymentReconciliationTest {

    private final MockStripePaymentProvider provider = new MockStripePaymentProvider();

    private PaymentChargeRequest charge(String key, String card) {
        return new PaymentChargeRequest(key, new BigDecimal("100.00"), "INR", card, "Order test");
    }

    @Test
    void repeatingAChargeWithTheSameKeyReturnsTheOriginalResultRatherThanChargingAgain() {
        PaymentResult first = provider.charge(charge("order:1:charge", "4242424242424242"));
        PaymentResult second = provider.charge(charge("order:1:charge", "4242424242424242"));

        assertThat(first.success()).isTrue();
        assertThat(second.transactionId()).isEqualTo(first.transactionId());
    }

    @Test
    void concurrentRetriesOfOneChargeProduceOneTransaction() {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<PaymentResult>> attempts = IntStream.range(0, 8)
                    .<Callable<PaymentResult>>mapToObj(i -> () -> provider.charge(charge("order:2:charge", "4242424242424242")))
                    .toList();

            Set<String> transactionIds = new HashSet<>();
            for (Future<PaymentResult> future : pool.invokeAll(attempts)) {
                transactionIds.add(future.get().transactionId());
            }

            assertThat(transactionIds).hasSize(1);
        } catch (Exception error) {
            throw new AssertionError(error);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aDifferentOrderGetsItsOwnCharge() {
        PaymentResult first = provider.charge(charge("order:3:charge", "4242424242424242"));
        PaymentResult second = provider.charge(charge("order:4:charge", "4242424242424242"));

        assertThat(second.transactionId()).isNotEqualTo(first.transactionId());
    }

    @Test
    void aChargeWithoutAnIdempotencyKeyIsRefusedRatherThanRisked() {
        assertThat(provider.charge(charge(null, "4242424242424242")).success()).isFalse();
        assertThat(provider.charge(charge("  ", "4242424242424242")).success()).isFalse();
    }

    @Test
    void replayingADeclineDoesNotTurnItIntoACharge() {
        PaymentResult declined = provider.charge(charge("order:5:charge", "4000000000000000"));
        PaymentResult replayed = provider.charge(charge("order:5:charge", "4242424242424242"));

        assertThat(declined.success()).isFalse();
        assertThat(replayed.success()).isFalse();
        assertThat(provider.lookup("order:5:charge")).map(PaymentResult::success).contains(false);
    }

    @Test
    void aSuccessfulChargeCanBeLookedUpAfterALostResponse() {
        PaymentResult result = provider.charge(charge("order:6:charge", "4242424242424242"));

        Optional<PaymentResult> found = provider.lookup("order:6:charge");

        assertThat(found).isPresent();
        assertThat(found.get().transactionId()).isEqualTo(result.transactionId());
    }

    @Test
    void lookingUpAKeyThatWasNeverChargedReportsNothing() {
        assertThat(provider.lookup("order:404:charge")).isEmpty();
        assertThat(provider.lookup(null)).isEmpty();
        assertThat(provider.lookup("")).isEmpty();
    }

    @Test
    void refundsAreIdempotentToo() {
        PaymentResult charged = provider.charge(charge("order:7:charge", "4242424242424242"));
        PaymentRefundRequest request = new PaymentRefundRequest(
                "order:7:refund", charged.transactionId(), new BigDecimal("100.00"), "INR", "could not fulfil");

        PaymentResult first = provider.refund(request);
        PaymentResult second = provider.refund(request);

        assertThat(first.success()).isTrue();
        assertThat(second.transactionId()).isEqualTo(first.transactionId());
    }

    @Test
    void refundingWithoutATransactionOrKeyIsRefused() {
        assertThat(provider.refund(new PaymentRefundRequest("order:8:refund", null,
                new BigDecimal("1.00"), "INR", "no transaction")).success()).isFalse();
        assertThat(provider.refund(new PaymentRefundRequest(null, "pi_x",
                new BigDecimal("1.00"), "INR", "no key")).success()).isFalse();
    }
}
