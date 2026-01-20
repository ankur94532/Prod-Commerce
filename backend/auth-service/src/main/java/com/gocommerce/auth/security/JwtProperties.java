package com.gocommerce.auth.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * Base64 or plain secret. For dev you can keep it simple.
     */
    private String secret = "change-this-secret-to-something-long-for-dev";
    private long accessTokenTtlMinutes = 60; // 1 hour
    private long refreshTokenTtlDays = 30; // 30 days

    public SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public Duration getAccessTokenTtl() {
        return Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public Duration getRefreshTokenTtl() {
        return Duration.ofDays(refreshTokenTtlDays);
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setAccessTokenTtlMinutes(long accessTokenTtlMinutes) {
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }
}
