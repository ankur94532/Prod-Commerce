package com.gocommerce.recommendation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class RestCatalogFallbackClient implements CatalogFallbackClient {

    private final RestClient restClient;
    private final String catalogBaseUrl;

    public RestCatalogFallbackClient(RestClient.Builder restClientBuilder,
                                     @Value("${catalog.base-url:http://localhost:8082}") String catalogBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.catalogBaseUrl = catalogBaseUrl;
    }

    @Override
    public List<CatalogProduct> fetchProducts(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        int pageSize = 100;
        int maxPages = 10;
        List<CatalogProduct> products = new java.util.ArrayList<>();
        int page = 0;
        int totalPages = 1;

        while (page < totalPages && page < maxPages) {
            Map<String, Object> response = restClient.get()
                    .uri(catalogBaseUrl + "/api/v1/products?page=" + page + "&size=" + pageSize)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            totalPages = response != null && response.get("totalPages") != null
                    ? asInteger(response.get("totalPages"), totalPages)
                    : totalPages;
            Object data = response != null ? response.get("data") : null;
            if (data instanceof List<?> pageProducts) {
                pageProducts.stream()
                        .filter(Map.class::isInstance)
                        .map(product -> toCatalogProduct((Map<?, ?>) product))
                        .filter(product -> product.productId() != null && product.productName() != null)
                        .forEach(products::add);
            }
            page++;
        }

        return products;
    }

    private CatalogProduct toCatalogProduct(Map<?, ?> product) {
        return new CatalogProduct(
                asString(product.get("id")),
                asString(product.get("name")),
                asBigDecimal(product.get("price")),
                asString(product.get("categorySlug")));
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static int asInteger(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
