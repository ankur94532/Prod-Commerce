package com.gocommerce.orders.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;

@Component
public class HttpCatalogClient implements CatalogClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final String internalToken;

    public HttpCatalogClient(@Value("${services.catalog.base-url}") String baseUrl,
                             @Value("${services.catalog.internal-token}") String internalToken) {
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    @Override
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogProductResponse(
            Long id,
            String name,
            BigDecimal price,
            String currency,
            Integer stockQuantity
    ) {}
}
