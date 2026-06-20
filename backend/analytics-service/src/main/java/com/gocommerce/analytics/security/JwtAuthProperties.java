package com.gocommerce.analytics.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtAuthProperties {

    /**
     * Must match the secret used in auth-service so we can verify tokens.
     */
    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public SecretKey getSigningKey() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("security.jwt.secret must be configured and at least 32 characters long");
        }
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
}
