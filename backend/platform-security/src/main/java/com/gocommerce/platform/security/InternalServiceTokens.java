package com.gocommerce.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time comparison for the shared service-to-service token. */
public final class InternalServiceTokens {

    public static final String HEADER = "X-Internal-Service-Token";

    private InternalServiceTokens() {
    }

    /** False when either side is missing, so an unset token never authenticates anything. */
    public static boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
