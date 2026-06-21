package com.gocommerce.analytics.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthServiceTest {

    @Test
    void parseToken_withValidSignature_returnsAuthUser() {
        // Service + token signed with the same secret
        String secret = "01234567890123456789012345678901"; // 32+ chars
        JwtAuthProperties props = new JwtAuthProperties();
        props.setSecret(secret);

        ObjectMapper objectMapper = new ObjectMapper();
        JwtAuthService service = new JwtAuthService(props, objectMapper);

        SecretKey signingKey = props.getSigningKey();

        String token = Jwts.builder()
                .setSubject("user-1")
                .claim("email", "user@example.com")
                .claim("role", "ADMIN")
                .setIssuedAt(new Date())
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        AuthUser authUser = service.parseToken(token);

        assertThat(authUser.id()).isEqualTo("user-1");
        assertThat(authUser.email()).isEqualTo("user@example.com");
        assertThat(authUser.role()).isEqualTo("ADMIN");
    }

    @Test
    void parseToken_withInvalidSignature_rejectsToken() {
        // Service uses secretA
        String secretA = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        JwtAuthProperties propsService = new JwtAuthProperties();
        propsService.setSecret(secretA);
        JwtAuthService service = new JwtAuthService(propsService, new ObjectMapper());

        // Token is signed with different key (secretB)
        String secretB = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
        SecretKey keyForToken = Keys.hmacShaKeyFor(secretB.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .setSubject("user-2")
                .claim("email", "user2@example.com")
                .claim("role", "USER")
                .setIssuedAt(new Date())
                .signWith(keyForToken, SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> service.parseToken(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
