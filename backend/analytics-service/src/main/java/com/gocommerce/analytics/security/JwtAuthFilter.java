package com.gocommerce.analytics.security;

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
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtAuthService jwtAuthService;

    public JwtAuthFilter(JwtAuthService jwtAuthService) {
        this.jwtAuthService = jwtAuthService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        log.info("JwtAuthFilter processing request: path={}, authHeader={}", path, header);

        // 2) If something already set Authentication (e.g. @WithMockUser in tests),
        // don't override it
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3) No Bearer token → proceed as anonymous; SecurityConfig will decide access
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            log.debug("No Bearer token found, continuing as anonymous");
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            AuthUser user = jwtAuthService.parseToken(token);

            String role = user.role() != null ? user.role() : "USER";
            String authority = "ROLE_" + role.toUpperCase();

            var auth = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    List.of(new SimpleGrantedAuthority(authority)));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

            log.info("Set SecurityContext for analytics user email={}, role={}, authority={}",
                    user.email(), user.role(), authority);

        } catch (Exception e) {
            log.warn("Failed to authenticate JWT for analytics: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            // Do NOT send 403 here; let the request continue as anonymous
        }

        filterChain.doFilter(request, response);
    }
}
