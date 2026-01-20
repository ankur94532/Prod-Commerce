package com.gocommerce.analytics.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class JwtAuthService {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthService.class);

    private final JwtAuthProperties props;
    private final ObjectMapper objectMapper;

    public JwtAuthService(JwtAuthProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public AuthUser parseToken(String token) {
        try {
            // 1) Normal path: verify signature using shared secret
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(props.getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            Claims body = jws.getBody();
            String id = body.getSubject();
            String email = body.get("email", String.class);
            String role = body.get("role", String.class); // "USER" / "ADMIN" from auth-service

            if (role == null) {
                role = "USER";
            }

            log.info("JWT verified OK. userId={}, email={}, role={}", id, email, role);

            return new AuthUser(id, email, role);
        } catch (JwtException ex) {
            // 2) If signature fails (likely secret mismatch), log and fall back to *unsafe*
            // decode for dev
            log.warn("JWT signature verification failed: {}. Falling back to unsigned decode (DEV ONLY).",
                    ex.getMessage());

            try {
                String[] parts = token.split("\\.");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Invalid JWT format");
                }

                String payloadJson = new String(
                        Base64.getUrlDecoder().decode(parts[1]),
                        StandardCharsets.UTF_8);

                JsonNode node = objectMapper.readTree(payloadJson);
                String id = node.path("sub").asText(null);
                String email = node.path("email").asText(null);
                String role = node.path("role").asText("USER");

                log.info("JWT decoded without verification. userId={}, email={}, role={}", id, email, role);

                return new AuthUser(id, email, role);
            } catch (Exception inner) {
                log.error("Failed to decode JWT payload", inner);
                throw ex; // rethrow original signature exception
            }
        }
    }
}
