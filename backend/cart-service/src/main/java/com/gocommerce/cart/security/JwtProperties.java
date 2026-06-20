package com.gocommerce.cart.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * Must be at least 32 bytes for HS256.
     */
    private String secret;

    public SecretKey getSigningKey() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("security.jwt.secret must be configured and at least 32 characters long");
        }
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
