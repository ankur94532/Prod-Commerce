package com.gocommerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTest {

    @Test
    void ipKeyResolver_returnsClientIp_whenRemoteAddressPresent() {
        RateLimiterConfig config = new RateLimiterConfig();
        KeyResolver resolver = config.ipKeyResolver();

        var request = MockServerHttpRequest
                .get("/api/v1/products")
                .remoteAddress(new InetSocketAddress("192.168.0.5", 12345))
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("192.168.0.5");
    }

    @Test
    void ipKeyResolver_returnsUnknown_whenNoRemoteAddress() {
        RateLimiterConfig config = new RateLimiterConfig();
        KeyResolver resolver = config.ipKeyResolver();

        var request = MockServerHttpRequest
                .get("/api/v1/products")
                .build(); // no remoteAddress set
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("unknown");
    }
}
