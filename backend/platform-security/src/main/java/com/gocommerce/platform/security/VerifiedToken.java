package com.gocommerce.platform.security;

import java.util.List;
import java.util.Set;

/**
 * A token whose signature, expiry, and type have all been checked.
 *
 * @param role  the single {@code role} claim, kept distinct from {@link #roles} so the
 *              principal exposes the same value the issuer wrote
 * @param roles the union of the {@code role} and {@code roles} claims
 */
public record VerifiedToken(
        String subject,
        String email,
        String fullName,
        String role,
        Set<String> roles,
        TokenType type) {

    /** Spring Security authority names, normalized to a single {@code ROLE_} prefix. */
    public List<String> authorities() {
        return roles.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.startsWith("ROLE_") ? value : "ROLE_" + value.toUpperCase())
                .distinct()
                .sorted()
                .toList();
    }

    public AuthenticatedUser principal() {
        return new AuthenticatedUser(subject, email, fullName, role);
    }
}
