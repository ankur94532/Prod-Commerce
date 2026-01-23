package com.gocommerce.orders.client;

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
}
