package com.gocommerce.analytics.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtAuthService {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthService.class);

    private final JwtAuthProperties props;

    public JwtAuthService(JwtAuthProperties props, ObjectMapper objectMapper) {
        this.props = props;
    }

    public AuthUser parseToken(String token) {
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(props.getSigningKey())
                .build()
                .parseClaimsJws(token);

        Claims body = jws.getBody();
        String id = body.getSubject();
        String email = body.get("email", String.class);
        String role = body.get("role", String.class);

        if (role == null) {
            role = "USER";
        }

        log.info("JWT verified OK. userId={}, email={}, role={}", id, email, role);

        return new AuthUser(id, email, role);
    }
}
