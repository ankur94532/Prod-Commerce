package com.gocommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeSecurityHeadersFilterTest {
    @Test
    void everyEdgeResponseCarriesBrowserSecurityHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/products").build());

        new EdgeSecurityHeadersFilter().filter(exchange, ignored -> Mono.empty()).block();

        var headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Content-Security-Policy")).contains("default-src 'none'", "frame-ancestors 'none'");
        assertThat(headers.getFirst("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }
}
