package com.gocommerce.platform.security;

/**
 * Access and refresh tokens are signed with the same key, so the only thing that keeps a
 * refresh token from being replayed as an access token is this claim. Every verification
 * states which type it expects.
 */
public enum TokenType {
    ACCESS,
    REFRESH;

    public static final String CLAIM = "type";

    public String claimValue() {
        return name().toLowerCase();
    }

    static TokenType fromClaim(String value) {
        for (TokenType type : values()) {
            if (type.claimValue().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new InvalidTokenException("Unknown token type '" + value + "'");
    }
}
