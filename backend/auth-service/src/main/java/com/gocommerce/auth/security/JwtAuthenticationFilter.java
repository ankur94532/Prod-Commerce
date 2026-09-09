package com.gocommerce.auth.security;

import com.gocommerce.platform.security.InvalidTokenException;
import com.gocommerce.platform.security.JwtVerifier;
import com.gocommerce.platform.security.TokenType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Unlike the other services, auth-service reloads the user on every request so a deleted or
 * role-changed account stops being accepted before its access token expires.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtVerifier jwtVerifier;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier, CustomUserDetailsService userDetailsService) {
        this.jwtVerifier = jwtVerifier;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = JwtVerifier.bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String userId = jwtVerifier.verify(token, TokenType.ACCESS).subject();
            var userDetails = userDetailsService.loadUserById(userId);
            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidTokenException error) {
            log.warn("Rejected bearer token on {} {}: {}", request.getMethod(), request.getRequestURI(),
                    error.getMessage());
            SecurityContextHolder.clearContext();
        } catch (RuntimeException error) {
            log.warn("Could not load the user named by an otherwise valid token: {}", error.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
