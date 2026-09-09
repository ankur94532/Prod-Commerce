package com.gocommerce.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying a valid access token. A request with no token, or with a
 * refresh token, continues unauthenticated and is then rejected by the authorization rules
 * rather than here, so public endpoints keep working.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtVerifier verifier;

    public JwtAuthenticationFilter(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = JwtVerifier.bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            VerifiedToken verified = verifier.verify(token, TokenType.ACCESS);
            List<SimpleGrantedAuthority> authorities = verified.authorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(verified.principal(), null, authorities));
        } catch (InvalidTokenException error) {
            // Log the reason only. The header and token never reach the log.
            log.warn("Rejected bearer token on {} {}: {}", request.getMethod(), request.getRequestURI(),
                    error.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
