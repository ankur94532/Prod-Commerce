package com.gocommerce.cart.security;

public record AuthenticatedUser(
        String id,
        String email,
        String fullName,
        String role) {
}
