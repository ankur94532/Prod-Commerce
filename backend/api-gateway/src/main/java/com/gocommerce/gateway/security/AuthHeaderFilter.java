package com.gocommerce.gateway.security;

import com.gocommerce.platform.security.InternalServiceTokens;
import com.gocommerce.platform.security.InvalidTokenException;
import com.gocommerce.platform.security.JwtVerifier;
import com.gocommerce.platform.security.TokenType;
import com.gocommerce.platform.security.VerifiedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The edge authentication filter.
 *
 * <p>It previously accepted any header beginning with "Bearer ", left token validation to
 * whichever service happened to be downstream, and treated every path <em>containing</em>
 * "/health" as public, so "/api/v1/admin/users/health" skipped the check entirely. It now
 * verifies the signature, expiry, and type of the token, matches public routes by method
 * and path segment, and strips client-supplied service credentials.
 */
@Component
public class AuthHeaderFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthHeaderFilter.class);

    /** Headers a client must never be able to set: they mean "a service is calling". */
    private static final Set<String> STRIPPED_HEADERS = Set.of(
            InternalServiceTokens.HEADER, "X-Auth-User-Id", "X-Auth-User-Role");

    /** Public prefixes per method. A prefix matches a whole path segment, never a substring. */
    private static final Map<HttpMethod, List<String>> PUBLIC_PREFIXES = Map.of(
            HttpMethod.GET, List.of(
                    "/api/v1/auth/health",
                    "/api/v1/auth/.well-known/jwks.json",
                    "/api/v1/products",
                    "/api/v1/catalog",
                    "/api/v1/search",
                    "/api/v1/recommendations"),
            HttpMethod.POST, List.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh"));

    private final JwtVerifier jwtVerifier;

    public AuthHeaderFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    static boolean isPublic(HttpMethod method, String path) {
        return PUBLIC_PREFIXES.getOrDefault(method, List.of()).stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> STRIPPED_HEADERS.forEach(headers::remove))
                .build();
        ServerWebExchange sanitized = exchange.mutate().request(request).build();

        if (isPublic(request.getMethod(), request.getPath().value())) {
            return chain.filter(sanitized);
        }

        String token = JwtVerifier.bearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return unauthorized(sanitized, "no bearer token");
        }

        try {
            VerifiedToken verified = jwtVerifier.verify(token, TokenType.ACCESS);
            log.debug("Authenticated {} {} for subject {}", request.getMethod(), request.getPath(),
                    verified.subject());
        } catch (InvalidTokenException error) {
            // The reason is logged; the token and the Authorization header never are.
            return unauthorized(sanitized, error.getMessage());
        }
        return chain.filter(sanitized);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.warn("Rejected {} {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
