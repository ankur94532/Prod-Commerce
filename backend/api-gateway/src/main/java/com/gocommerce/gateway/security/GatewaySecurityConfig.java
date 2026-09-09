package com.gocommerce.gateway.security;

import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.platform.security.JwtVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties properties) {
        return new JwtVerifier(properties);
    }
}
