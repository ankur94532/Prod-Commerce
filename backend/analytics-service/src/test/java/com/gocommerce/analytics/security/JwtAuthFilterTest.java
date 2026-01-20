package com.gocommerce.analytics.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_leavesAuthenticationNull_whenNoAuthorizationHeader() throws Exception {
        // Stub that should never be called in this test
        JwtAuthService stubService = new JwtAuthService(null, null) {
            @Override
            public AuthUser parseToken(String token) {
                throw new AssertionError("parseToken should not be called");
            }
        };

        JwtAuthFilter filter = new JwtAuthFilter(stubService);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/analytics/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_setsAuthentication_whenValidBearerTokenPresent() throws Exception {
        AuthUser user = new AuthUser("id-1", "admin@example.com", "ADMIN");

        // Stub that returns a fixed user for a known token
        JwtAuthService stubService = new JwtAuthService(null, null) {
            @Override
            public AuthUser parseToken(String token) {
                if (!"good-token".equals(token)) {
                    throw new AssertionError("Unexpected token: " + token);
                }
                return user;
            }
        };

        JwtAuthFilter filter = new JwtAuthFilter(stubService);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/analytics/summary");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(user);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void doFilter_doesNotOverrideExistingAuthentication() throws Exception {
        // Stub that must NOT be called because auth is already set
        JwtAuthService stubService = new JwtAuthService(null, null) {
            @Override
            public AuthUser parseToken(String token) {
                throw new AssertionError(
                        "parseToken should not be called when authentication already exists");
            }
        };

        JwtAuthFilter filter = new JwtAuthFilter(stubService);

        var existingAuth = new UsernamePasswordAuthenticationToken("existing", "pwd");
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/analytics/summary");
        request.addHeader("Authorization", "Bearer some-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(existingAuth);
    }
}
