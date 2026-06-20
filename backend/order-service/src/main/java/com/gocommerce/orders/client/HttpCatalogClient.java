package com.gocommerce.orders.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class HttpCatalogClient implements CatalogClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public HttpCatalogClient(@Value("${services.catalog.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public ProductSnapshot getProductSnapshot(String productId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/v1/internal/products/{productId}")
                .buildAndExpand(productId)
                .toUriString();

        ResponseEntity<CatalogProductResponse> response = restTemplate.getForEntity(url, CatalogProductResponse.class);
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

        ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);

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

        ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "Failed to compensate stock for product " + productId +
                            ", status=" + response.getStatusCode()
            );
        }
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
