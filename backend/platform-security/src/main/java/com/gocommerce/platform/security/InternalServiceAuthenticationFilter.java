package com.gocommerce.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Grants {@code ROLE_INTERNAL} to callers presenting the shared service token, and rejects
 * nothing on its own. Use it where an endpoint should accept either a calling service or an
 * administrator, and let the authorization rules decide; use
 * {@link InternalServiceTokenFilter} where only a service may ever call.
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE = "INTERNAL";

    private final String expectedToken;

    public InternalServiceAuthenticationFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String provided = request.getHeader(InternalServiceTokens.HEADER);
        if (provided != null && SecurityContextHolder.getContext().getAuthentication() == null
                && InternalServiceTokens.matches(expectedToken, provided)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        }
        chain.doFilter(request, response);
    }
}
