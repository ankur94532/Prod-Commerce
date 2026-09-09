package com.gocommerce.platform.security;

/** Principal placed in the security context by {@link JwtAuthenticationFilter}. */
public record AuthenticatedUser(
        String id,
        String email,
        String fullName,
        String role) {
}
