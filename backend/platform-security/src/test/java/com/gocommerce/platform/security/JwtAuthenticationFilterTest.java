package com.gocommerce.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private JwtIssuer issuer;
    private JwtAuthenticationFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test_service_secret_very_long_1234567890");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        issuer = new JwtIssuer(properties);
        filter = new JwtAuthenticationFilter(new JwtVerifier(properties));
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart/123");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }

    private void run(MockHttpServletRequest request, FilterChain chain) throws ServletException, IOException {
        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void aValidAccessTokenPopulatesThePrincipalAndAuthorities() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request(
                "Bearer " + issuer.accessToken("user-1", "user@example.com", "User One", "USER"));

        run(request, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedUser("user-1", "user@example.com", "User One", "USER"));
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(chain).doFilter(request, response);
    }

    @Test
    void aRequestWithoutAnAuthorizationHeaderStaysAnonymous() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request(null);

        run(request, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void aRefreshTokenNeverAuthenticatesAServiceRequest() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("Bearer " + issuer.refreshToken("user-1"));

        run(request, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void anInvalidTokenClearsTheContextButLetsTheRequestContinue() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("Bearer invalid-token");

        run(request, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void aNonBearerSchemeIsIgnored() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("Basic dXNlcjpwYXNz");

        run(request, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void anAlreadyAuthenticatedRequestIsNotOverwritten() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("existing", null));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request(
                "Bearer " + issuer.accessToken("user-1", "user@example.com", "User One", "ADMIN"));

        run(request, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("existing");
        verify(chain).doFilter(request, response);
    }
}
