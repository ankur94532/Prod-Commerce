package com.gocommerce.gateway.security;

import com.gocommerce.platform.security.InternalServiceTokens;
import com.gocommerce.platform.security.JwtIssuer;
import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.platform.security.JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthHeaderFilterTest {

    private static final String SECRET = "gateway_test_secret_of_at_least_32_chars";

    private static class StubGatewayFilterChain implements GatewayFilterChain {
        boolean called = false;
        ServerWebExchange forwarded;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            this.forwarded = exchange;
            return Mono.empty();
        }
    }

    private JwtIssuer issuer;
    private AuthHeaderFilter filter;
    private StubGatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        issuer = new JwtIssuer(properties);
        filter = new AuthHeaderFilter(new JwtVerifier(properties));
        chain = new StubGatewayFilterChain();
    }

    private String accessToken() {
        return issuer.accessToken("user-1", "user@example.com", "User One", "USER");
    }

    private MockServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void publicReadsPassWithoutCredentials() {
        for (String path : new String[] { "/api/v1/products", "/api/v1/products/slug", "/api/v1/search",
                "/api/v1/search/vector", "/api/v1/recommendations/trending", "/api/v1/auth/health",
                "/api/v1/auth/.well-known/jwks.json" }) {
            chain = new StubGatewayFilterChain();
            MockServerWebExchange exchange = run(MockServerHttpRequest.get(path).build());

            assertThat(chain.called).as(path).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).as(path).isNull();
        }
    }

    @Test
    void signInEndpointsArePublicForPostOnly() {
        for (String path : new String[] { "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh" }) {
            chain = new StubGatewayFilterChain();
            run(MockServerHttpRequest.post(path).build());
            assertThat(chain.called).as(path).isTrue();
        }

        chain = new StubGatewayFilterChain();
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/auth/me").build());
        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void searchMutationsAreNotPublicEvenThoughSearchReadsAre() {
        // The reindex endpoint rebuilds the live index; it must never be anonymous.
        for (MockServerHttpRequest request : new MockServerHttpRequest[] {
                MockServerHttpRequest.post("/api/v1/search/reindex").build(),
                MockServerHttpRequest.post("/api/v1/search/index-product").build(),
                MockServerHttpRequest.delete("/api/v1/search/products/p1").build() }) {
            chain = new StubGatewayFilterChain();
            MockServerWebExchange exchange = run(request);

            assertThat(chain.called).as(request.getPath().value()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void aPathThatMerelyContainsHealthIsNotPublic() {
        // The old filter used path.contains("/health"), which made this request anonymous.
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/admin/users/health").build());

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aPrefixMustMatchAWholeSegment() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/products-admin/secrets").build());

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actuatorIsNoLongerPublicThroughTheGateway() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/actuator/prometheus").build());

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedPathWithoutATokenIsRejected() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/cart/items").build());

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anUnverifiableBearerStringIsRejected() {
        // "Bearer abc123" used to be accepted purely because of its prefix.
        for (String header : new String[] { "Bearer abc123", "Bearer ", "Basic dXNlcjpwYXNz",
                "Bearer " + new JwtIssuer(otherKey()).accessToken("user-1", null, null, "ADMIN") }) {
            chain = new StubGatewayFilterChain();
            MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/cart/items")
                    .header(HttpHeaders.AUTHORIZATION, header).build());

            assertThat(chain.called).as(header).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private static JwtProperties otherKey() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("a_different_gateway_secret_of_32_chars");
        return properties;
    }

    @Test
    void aRefreshTokenIsRejectedAtTheEdge() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.refreshToken("user-1")).build());

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anExpiredTokenIsRejected() {
        JwtProperties expiring = new JwtProperties();
        expiring.setSecret(SECRET);
        expiring.setAccessTokenTtl(Duration.ofSeconds(-1));

        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + new JwtIssuer(expiring).accessToken("user-1", null, null, "USER"))
                .build());

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aValidAccessTokenIsForwarded() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken()).build());

        assertThat(chain.called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void clientSuppliedServiceCredentialsAreStrippedBeforeForwarding() {
        // Otherwise a caller could claim to be an internal service to catalog or search.
        run(MockServerHttpRequest.get("/api/v1/products")
                .header(InternalServiceTokens.HEADER, "guessed-token")
                .header("X-Auth-User-Id", "someone-else")
                .header("X-Auth-User-Role", "ADMIN")
                .build());

        HttpHeaders forwarded = chain.forwarded.getRequest().getHeaders();
        assertThat(forwarded.getFirst(InternalServiceTokens.HEADER)).isNull();
        assertThat(forwarded.getFirst("X-Auth-User-Id")).isNull();
        assertThat(forwarded.getFirst("X-Auth-User-Role")).isNull();
    }

    @Test
    void strippingAlsoAppliesToAuthenticatedRequests() {
        run(MockServerHttpRequest.get("/api/v1/cart/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .header(InternalServiceTokens.HEADER, "guessed-token")
                .build());

        assertThat(chain.called).isTrue();
        assertThat(chain.forwarded.getRequest().getHeaders().getFirst(InternalServiceTokens.HEADER)).isNull();
    }

    @Test
    void getOrder_returnsMinusOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
