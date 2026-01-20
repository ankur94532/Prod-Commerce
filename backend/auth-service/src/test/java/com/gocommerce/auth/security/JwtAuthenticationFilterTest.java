package com.gocommerce.auth.security;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;

    private User testUser;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test_secret_very_long_1234567890");
        jwtService = new JwtService(props);

        testUser = new User("user@example.com", "hash", "Test User", Role.USER) {
            @Override
            public String getId() {
                return "user-123";
            }
        };
        userPrincipal = new UserPrincipal(testUser);

        // Stub CustomUserDetailsService without Mockito (avoid ByteBuddy issues)
        userDetailsService = new CustomUserDetailsService(null) {
            @Override
            public org.springframework.security.core.userdetails.UserDetails loadUserById(String id) {
                return userPrincipal;
            }

            @Override
            public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String username) {
                return userPrincipal;
            }
        };

        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_leavesAuthenticationNull_whenNoAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_setsAuthentication_whenValidBearerTokenPresent() throws ServletException, IOException {
        String token = jwtService.generateAccessToken(testUser);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) auth.getPrincipal()).getUsername())
                .isEqualTo("user@example.com");

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_clearsContextOnInvalidToken_butContinuesChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        HttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
