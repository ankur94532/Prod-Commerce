package com.gocommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class AuthHeaderFilterTest {

    private static class StubGatewayFilterChain implements GatewayFilterChain {
        boolean called = false;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            return Mono.empty();
        }
    }

    @Test
    void publicPath_allowsRequestWithoutAuthHeader() {
        AuthHeaderFilter filter = new AuthHeaderFilter();
        StubGatewayFilterChain chain = new StubGatewayFilterChain();

        var request = MockServerHttpRequest
                .get("/api/v1/products")
                .build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        // response status should not be set to 401
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectedPath_withoutAuthHeader_returnsUnauthorizedAndDoesNotCallChain() {
        AuthHeaderFilter filter = new AuthHeaderFilter();
        StubGatewayFilterChain chain = new StubGatewayFilterChain();

        var request = MockServerHttpRequest
                .get("/api/v1/cart/items")
                .build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedPath_withBearerToken_callsChain() {
        AuthHeaderFilter filter = new AuthHeaderFilter();
        StubGatewayFilterChain chain = new StubGatewayFilterChain();

        var request = MockServerHttpRequest
                .get("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer abc123")
                .build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void getOrder_returnsMinusOne() {
        AuthHeaderFilter filter = new AuthHeaderFilter();
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
