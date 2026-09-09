package com.gocommerce.platform.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokensTest {

    @Test
    void matchingTokensAuthenticate() {
        assertThat(InternalServiceTokens.matches("shared-token-value", "shared-token-value")).isTrue();
    }

    @Test
    void anyMismatchFails() {
        assertThat(InternalServiceTokens.matches("shared-token-value", "shared-token-valu")).isFalse();
        assertThat(InternalServiceTokens.matches("shared-token-value", "Shared-token-value")).isFalse();
        assertThat(InternalServiceTokens.matches("shared-token-value", "shared-token-value ")).isFalse();
    }

    @Test
    void anUnsetExpectedTokenNeverAuthenticates() {
        // Otherwise forgetting to set the secret would silently open every internal endpoint.
        assertThat(InternalServiceTokens.matches(null, "anything")).isFalse();
        assertThat(InternalServiceTokens.matches("", "")).isFalse();
        assertThat(InternalServiceTokens.matches("   ", "   ")).isFalse();
    }

    @Test
    void aMissingProvidedTokenNeverAuthenticates() {
        assertThat(InternalServiceTokens.matches("shared-token-value", null)).isFalse();
        assertThat(InternalServiceTokens.matches("shared-token-value", "")).isFalse();
    }
}
