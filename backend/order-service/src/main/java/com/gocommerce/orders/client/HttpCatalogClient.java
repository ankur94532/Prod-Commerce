package com.gocommerce.orders.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class HttpCatalogClient implements CatalogClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalToken;

    public HttpCatalogClient(@Value("${services.catalog.base-url}") String baseUrl,
                             @Value("${services.catalog.internal-token}") String internalToken,
                             @Value("${services.catalog.connect-timeout:2s}") Duration connectTimeout,
                             @Value("${services.catalog.read-timeout:4s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    @Override
    @CircuitBreaker(name = "catalogClient", fallbackMethod = "getProductSnapshotFallback")
    @Retry(name = "catalogClient")
    public ProductSnapshot getProductSnapshot(String productId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/v1/internal/products/{productId}")
                .buildAndExpand(productId)
                .toUriString();

        ResponseEntity<CatalogProductResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders()),
                CatalogProductResponse.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Failed to fetch product snapshot for product " + productId);
        }

        CatalogProductResponse product = response.getBody();
        if (product.price() == null) {
            throw new IllegalStateException("Catalog product has no price: " + productId);
        }

        return new ProductSnapshot(
                String.valueOf(product.id()),
                product.name(),
                product.price(),
                product.currency()
        );
    }

    @Override
    @CircuitBreaker(name = "catalogClient", fallbackMethod = "decrementStockFallback")
    @Retry(name = "catalogClient")
    public void decrementStock(String productId, int quantity) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/v1/internal/inventory/products/{productId}/decrement")
                .queryParam("quantity", quantity)
                .buildAndExpand(productId)
                .toUriString();

        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Void.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "Failed to decrement stock for product " + productId +
                            ", status=" + response.getStatusCode()
            );
        }
    }

    @Override
    @CircuitBreaker(name = "catalogClient", fallbackMethod = "incrementStockFallback")
    @Retry(name = "catalogCompensation")
    public void incrementStock(String productId, int quantity) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/v1/internal/inventory/products/{productId}/increment")
                .queryParam("quantity", quantity)
                .buildAndExpand(productId)
                .toUriString();

        ResponseEntity<Void> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Void.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "Failed to compensate stock for product " + productId +
                            ", status=" + response.getStatusCode()
            );
        }
    }

    private HttpHeaders internalHeaders() {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("services.catalog.internal-token must be configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
        return headers;
    }

    @SuppressWarnings("unused")
    private ProductSnapshot getProductSnapshotFallback(String productId, Throwable ex) {
        throw new IllegalStateException("Catalog snapshot unavailable for product " + productId, ex);
    }

    @SuppressWarnings("unused")
    private void decrementStockFallback(String productId, int quantity, Throwable ex) {
        throw new IllegalStateException("Catalog stock decrement unavailable for product " + productId, ex);
    }

    @SuppressWarnings("unused")
    private void incrementStockFallback(String productId, int quantity, Throwable ex) {
        throw new IllegalStateException("Catalog stock compensation unavailable for product " + productId, ex);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogProductResponse(
            Long id,
            String name,
            BigDecimal price,
            String currency,
            Integer stockQuantity
    ) {}
}
