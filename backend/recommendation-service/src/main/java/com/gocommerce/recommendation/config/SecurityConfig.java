package com.gocommerce.recommendation.config;

import com.gocommerce.platform.security.InternalServiceTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private static final String INTERNAL_PREFIX = "/internal/";

    @Value("${security.internal.service-token:}")
    private String internalServiceToken;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // Trending results are non-personalized and intentionally public.
                        .requestMatchers("/api/v1/recommendations/**").permitAll()
                        // Guarded by the internal-token filter below, not by a user token.
                        .requestMatchers(INTERNAL_PREFIX + "**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(InternalServiceTokenFilter.forPathPrefix(internalServiceToken, INTERNAL_PREFIX),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
