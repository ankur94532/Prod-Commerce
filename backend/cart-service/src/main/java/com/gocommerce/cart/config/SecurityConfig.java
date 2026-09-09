package com.gocommerce.cart.config;

import com.gocommerce.platform.security.JwtAuthenticationFilter;
import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.platform.security.JwtVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
// A filter chain is meaningless outside a servlet web application, and the migration job
// runs with web-application-type=none. Without this the job fails to start, because
// HttpSecurity is only auto-configured for servlet applications.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties properties) {
        return new JwtVerifier(properties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        return new JwtAuthenticationFilter(jwtVerifier);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring Security filters the ERROR dispatch too, so without this an
                        // unauthenticated-looking 401 is returned for every server error. That
                        // hides real outages: the availability alert matches 5xx, and a 401
                        // never trips it.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/cart/health").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
