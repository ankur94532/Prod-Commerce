package com.gocommerce.search.config;

import com.gocommerce.platform.security.InternalServiceAuthenticationFilter;
import com.gocommerce.platform.security.AdminAuditFilter;
import com.gocommerce.platform.security.JwtAuthenticationFilter;
import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.platform.security.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Search-service previously had no security configuration at all. Reading is public, but
 * the three mutating endpoints are not: reindex deletes and recreates the live index, and
 * the single-document endpoints can insert or remove anything from search results.
 */
// A filter chain is meaningless outside a servlet web application, and the migration job
// runs with web-application-type=none. Without this the job fails to start, because
// HttpSecurity is only auto-configured for servlet applications.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

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
                        // Spring Security filters the ERROR dispatch too, so without this an
                        // unauthenticated-looking 401 is returned for every server error. That
                        // hides real outages: the availability alert matches 5xx, and a 401
                        // never trips it.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        // Spring Security filters the ERROR dispatch too, so without this an
                        // unauthenticated-looking 401 is returned for every server error. That
                        // hides real outages: the availability alert matches 5xx, and a 401
                        // never trips it.
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Shoppers search without signing in.
                        .requestMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()
                        // Catalog keeps the index in step; an administrator can also force a rebuild.
                        .requestMatchers(HttpMethod.POST, "/api/v1/search/reindex")
                        .hasAnyRole(InternalServiceAuthenticationFilter.ROLE, "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/search/index-product")
                        .hasRole(InternalServiceAuthenticationFilter.ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/search/products/**")
                        .hasRole(InternalServiceAuthenticationFilter.ROLE)
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalServiceAuthenticationFilter(internalServiceToken),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AdminAuditFilter(request ->
                                "POST".equals(request.getMethod())
                                        && "/api/v1/search/reindex".equals(request.getRequestURI())),
                        JwtAuthenticationFilter.class)
                .build();
    }
}
