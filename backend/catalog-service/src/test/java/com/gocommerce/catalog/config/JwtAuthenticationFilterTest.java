package com.gocommerce.catalog.config;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test_catalog_secret_very_long_1234567890";

    private SecurityConfig.JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityConfig.JwtAuthenticationFilter(SECRET);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String createToken(String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(Map.of(
                        "email", "admin@example.com",
                        "role", role
                ))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(3600)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }

    @Test
    void shouldNotFilter_skipsActuatorAndGetProducts() {
        MockHttpServletRequest actuatorReq = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletRequest publicProductsReq = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletRequest adminReq = new MockHttpServletRequest("GET", "/api/v1/admin/products");

        assertThat(filter.shouldNotFilter(actuatorReq)).isTrue();
        assertThat(filter.shouldNotFilter(publicProductsReq)).isTrue();
        assertThat(filter.shouldNotFilter(adminReq)).isFalse();
    }

    @Test
    void doFilterInternal_doesNothingWhenNoAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/products");
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_setsAuthenticationOnValidToken() throws ServletException, IOException {
        String token = createToken("admin-user-id", "ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/products");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("admin-user-id");
        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_ADMIN");

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_clearsContextOnInvalidToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/products");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
