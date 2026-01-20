package com.gocommerce.gateway.security;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        // 🔓 Public routes (no Authorization header required)
        boolean isPublic =
                // auth-service (login, register, refresh)
                path.startsWith("/api/v1/auth")
                        // catalog / product browsing (home + product detail)
                        || path.startsWith("/api/v1/catalog")
                        || path.startsWith("/api/v1/products")
                        // search page
                        || path.startsWith("/api/v1/search")
                        // homepage recommendations (non-personalized)
                        || path.startsWith("/api/v1/recommendations")
                        // health / metrics
                        || path.startsWith("/actuator")
                        || path.contains("/health");

        if (isPublic) {
            return chain.filter(exchange);
        }

        // 🔐 Protected routes – must have Bearer token
        List<String> authHeaders = exchange.getRequest()
                .getHeaders()
                .getOrEmpty(HttpHeaders.AUTHORIZATION);

        boolean hasBearer = !authHeaders.isEmpty()
                && authHeaders.get(0) != null
                && authHeaders.get(0).startsWith("Bearer ");

        if (!hasBearer) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Token validity is checked by downstream services (auth-service JWT logic)
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run early
        return -1;
    }
}
