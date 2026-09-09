package com.gocommerce.platform.security;

import io.jsonwebtoken.Jwts;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues typed tokens. A refresh token carries no role or profile claims: it is only good
 * for exchange at the refresh endpoint, and {@link JwtVerifier} refuses it everywhere else.
 */
public class JwtIssuer {

    private final JwtProperties properties;

    public JwtIssuer(JwtProperties properties) {
        this.properties = properties;
    }

    public String accessToken(String subject, String email, String fullName, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TokenType.CLAIM, TokenType.ACCESS.claimValue());
        if (email != null) {
            claims.put("email", email);
        }
        if (fullName != null) {
            claims.put("fullName", fullName);
        }
        if (role != null) {
            claims.put("role", role);
        }
        return sign(subject, claims, properties.getAccessTokenTtl());
    }

    public String refreshToken(String subject) {
        return sign(subject, Map.of(TokenType.CLAIM, TokenType.REFRESH.claimValue()), properties.getRefreshTokenTtl());
    }

    private String sign(String subject, Map<String, Object> claims, java.time.Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttl)))
                .signWith(properties.getSigningKey())
                .compact();
    }
}
