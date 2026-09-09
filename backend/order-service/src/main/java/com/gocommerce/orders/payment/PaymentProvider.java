package com.gocommerce.orders.payment;

import java.util.Optional;

/**
 * A payment provider must answer three questions, not one. Charging is the obvious one.
 * The other two are what make recovery safe:
 *
 * <ul>
 *   <li>{@link #lookup(String)} — "did this charge actually happen?" A lost response is
 *       indistinguishable from a failure at the caller, so recovery has to be able to ask.
 *       Without it, an abandoned order can be cancelled while the customer has been charged.</li>
 *   <li>{@link #refund(PaymentRefundRequest)} — a charge that succeeded before a later step
 *       failed has to be given back, not merely forgotten.</li>
 * </ul>
 *
 * Every operation is keyed by an idempotency key and must return the same outcome for a
 * repeated key rather than performing the operation again.
 */
public interface PaymentProvider {

    PaymentResult charge(PaymentChargeRequest request);

    /**
     * The provider's own record for an idempotency key.
     *
     * @return empty when the provider has no record of it, which means no money moved
     * @throws PaymentProviderUnavailableException when the provider cannot be reached, which
     *         is emphatically not the same as "no charge exists"
     */
    Optional<PaymentResult> lookup(String idempotencyKey);

    PaymentResult refund(PaymentRefundRequest request);
}
