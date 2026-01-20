package com.gocommerce.cart.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtProperties props;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret("test_cart_secret_very_long_1234567890");
        jwtService = new JwtService(props);
    }

    @Test
    void parseToken_parsesSignedTokenWithClaims() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .setSubject("user-1")
                .addClaims(Map.of(
                        "email", "user@example.com",
                        "fullName", "User One",
                        "role", "USER"))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(3600)))
                .signWith(props.getSigningKey())
                .compact();

        Jws<Claims> jws = jwtService.parseToken(token);

        Claims body = jws.getBody();
        assertThat(body.getSubject()).isEqualTo("user-1");
        assertThat(body.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(body.get("fullName", String.class)).isEqualTo("User One");
        assertThat(body.get("role", String.class)).isEqualTo("USER");
        assertThat(body.getExpiration()).isAfter(body.getIssuedAt());
    }
}
