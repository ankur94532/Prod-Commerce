package com.gocommerce.cart.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import io.jsonwebtoken.Jwts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private JwtProperties props;
    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret("test_cart_secret_very_long_1234567890");
        jwtService = new JwtService(props);
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String createToken(String userId, String email, String fullName, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(userId)
                .addClaims(Map.of(
                        "email", email,
                        "fullName", fullName,
                        "role", role))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(3600)))
                .signWith(props.getSigningKey())
                .compact();
    }

    @Test
    void doFilter_leavesAuthenticationNull_whenNoAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart/123");
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_doesNotOverrideExistingAuthentication() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("existing", null));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart/123");
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("existing");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_setsAuthentication_whenValidBearerTokenPresent() throws ServletException, IOException {
        String token = createToken("user-1", "user@example.com", "User One", "USER");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart/123");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);

        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertThat(principal.id()).isEqualTo("user-1");
        assertThat(principal.email()).isEqualTo("user@example.com");
        assertThat(principal.role()).isEqualTo("USER");

        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_USER");

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_clearsContextOnInvalidToken_butContinuesChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart/123");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
