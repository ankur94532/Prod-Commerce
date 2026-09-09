package com.gocommerce.cart.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CartConflictException extends RuntimeException {

    public CartConflictException(String message) {
        super(message);
    }
}
