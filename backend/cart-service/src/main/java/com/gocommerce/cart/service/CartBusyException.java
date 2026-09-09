package com.gocommerce.cart.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CartBusyException extends RuntimeException {

    public CartBusyException() {
        super("Cart is being updated; retry with the same idempotency key");
    }
}
