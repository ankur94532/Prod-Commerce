package com.gocommerce.platform.security;

/** Thrown when a token is absent, malformed, expired, wrongly signed, or of the wrong type. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
