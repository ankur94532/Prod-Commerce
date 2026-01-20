package com.gocommerce.auth.security;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test_secret_very_long_1234567890");
        props.setAccessTokenTtlMinutes(60);
        props.setRefreshTokenTtlDays(30);
        jwtService = new JwtService(props);
    }

    private User buildTestUser() {
        return new User("user@example.com", "pw", "Test User", Role.ADMIN) {
            @Override
            public String getId() {
                return "user-123";
            }
        };
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        User user = buildTestUser();

        String token = jwtService.generateAccessToken(user);
        Jws<Claims> jws = jwtService.parseToken(token);

        Claims body = jws.getBody();
        assertThat(body.getSubject()).isEqualTo("user-123");
        assertThat(body.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(body.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(body.getExpiration()).isAfter(body.getIssuedAt());
    }

    @Test
    void generateRefreshToken_hasSubjectAndExpiry() {
        User user = buildTestUser();

        String token = jwtService.generateRefreshToken(user);
        Jws<Claims> jws = jwtService.parseToken(token);

        Claims body = jws.getBody();
        assertThat(body.getSubject()).isEqualTo("user-123");
        assertThat(body.getExpiration()).isAfter(body.getIssuedAt());
    }
}
