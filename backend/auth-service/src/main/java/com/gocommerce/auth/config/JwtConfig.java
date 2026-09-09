package com.gocommerce.auth.config;

import com.gocommerce.platform.security.JwtIssuer;
import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.platform.security.JwtVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auth-service is the only issuer; every other service only verifies. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtIssuer jwtIssuer(JwtProperties properties) {
        return new JwtIssuer(properties);
    }

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties properties) {
        return new JwtVerifier(properties);
    }
}
