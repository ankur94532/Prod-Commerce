package com.gocommerce.platform.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Shared JWT configuration. Bound from {@code security.jwt.*} by whichever service
 * registers it; the secret is never logged and never exposed by a getter.
 */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private static final int MINIMUM_SECRET_LENGTH = 32;

    private String secret;
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public SecretKey getSigningKey() {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "security.jwt.secret must be configured and at least " + MINIMUM_SECRET_LENGTH + " characters long");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
