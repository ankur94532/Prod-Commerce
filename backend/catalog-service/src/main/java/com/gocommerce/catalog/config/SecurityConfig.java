package com.gocommerce.catalog.config;

import com.gocommerce.platform.security.InternalServiceTokenFilter;
import com.gocommerce.platform.security.AdminAuditFilter;
import com.gocommerce.platform.security.JwtAuthenticationFilter;
import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.platform.security.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String INTERNAL_PREFIX = "/api/v1/internal/";

    @Value("${security.internal.service-token:}")
    private String internalServiceToken;

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties properties) {
        return new JwtVerifier(properties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtVerifier jwtVerifier) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        // Authenticated by the internal-token filter below, not by a user token.
                        .requestMatchers(INTERNAL_PREFIX + "**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(InternalServiceTokenFilter.forPathPrefix(internalServiceToken, INTERNAL_PREFIX),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AdminAuditFilter(request ->
                                request.getRequestURI().startsWith("/api/v1/admin/products")
                                        && !"GET".equals(request.getMethod())),
                        JwtAuthenticationFilter.class)
                .build();
    }
}
