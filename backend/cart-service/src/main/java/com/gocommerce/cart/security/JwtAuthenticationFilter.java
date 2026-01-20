package com.gocommerce.cart.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // If something already set Authentication (e.g. tests with @WithMockUser),
        // don't overwrite
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Jws<Claims> jws = jwtService.parseToken(token);
            Claims claims = jws.getBody();

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String fullName = claims.get("fullName", String.class);
            String role = claims.get("role", String.class);

            AuthenticatedUser principal = new AuthenticatedUser(userId, email, fullName, role);

            List<SimpleGrantedAuthority> authorities = role != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    : List.of();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
                    null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Cart JWT authenticated userId={}, email={}, role={} for path={}",
                    userId, email, role, path);
        } catch (Exception e) {
            log.warn("JWT validation failed for cart-service: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
