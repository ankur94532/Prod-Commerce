package com.gocommerce.gateway.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
public class FallbackController {

    /**
     * Fallback used by the Gateway CircuitBreaker for the search route.
     * Matches fallbackUri: "forward:/fallback/search" in application.yml.
     */
    @GetMapping(path = "/fallback/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> searchFallback(ServerWebExchange exchange) {
        return Mono.just(
                Map.of(
                        "items", List.of(),
                        "total", 0,
                        "fallback", true,
                        "message", "Search service is currently unavailable. Showing empty results from gateway fallback.",
                        "source", "api-gateway-circuit-breaker"
                )
        );
    }
}
