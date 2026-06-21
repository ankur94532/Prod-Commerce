package com.gocommerce.catalog.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.internal.service-token:}")
    private String internalServiceToken;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtSecret);
        InternalServiceTokenFilter internalServiceTokenFilter = new InternalServiceTokenFilter(internalServiceToken);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers("/api/v1/internal/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(internalServiceTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Protects catalog's internal endpoints used by other backend services.
     * This keeps inventory and product snapshot APIs away from public clients even if catalog-service is reachable.
     */
    static class InternalServiceTokenFilter extends OncePerRequestFilter {
        private static final String HEADER = "X-Internal-Service-Token";

        private final String expectedToken;

        InternalServiceTokenFilter(String expectedToken) {
            this.expectedToken = expectedToken;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/api/v1/internal/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String provided = request.getHeader(HEADER);
            if (expectedToken == null || expectedToken.isBlank()
                    || provided == null || provided.isBlank()
                    || !MessageDigest.isEqual(
                    expectedToken.getBytes(StandardCharsets.UTF_8),
                    provided.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal service token");
                return;
            }

            filterChain.doFilter(request, response);
        }
    }

    /**
     * Reads Authorization: Bearer <token>, validates HS256 JWT, extracts role(s),
     * and sets Authentication so @PreAuthorize can enforce admin APIs.
     */
    static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final SecretKey key;

        JwtAuthenticationFilter(String secret) {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            String method = request.getMethod();

            if (path.startsWith("/actuator")) return true;
            if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) return true;
            if (path.startsWith("/api/v1/internal/")) return true;
            return "GET".equals(method) && path.startsWith("/api/v1/products");
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String subject = claims.getSubject();
                Set<String> roles = new HashSet<>();

                Object singleRole = claims.get("role");
                if (singleRole instanceof String s) {
                    roles.add(s);
                }

                Object rolesClaim = claims.get("roles");
                if (rolesClaim instanceof Collection<?> col) {
                    for (Object o : col) {
                        if (o != null) roles.add(o.toString());
                    }
                }

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .filter(Objects::nonNull)
                        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(subject, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }

            filterChain.doFilter(request, response);
        }
    }
}
