package com.gocommerce.orders.payment;

/**
 * The provider could not be reached or gave no usable answer. Distinct from "no charge
 * exists": treating an outage as an absent charge is how a paying customer gets cancelled.
 */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message) {
        super(message);
    }

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
