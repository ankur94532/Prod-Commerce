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
    private String secret = "this_is_a_dev_secret_change_later_1234567890";

    public SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
