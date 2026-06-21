package com.gocommerce.catalog.client;

import com.gocommerce.catalog.entity.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Stream;

@Component
public class SearchIndexClient {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexClient.class);

    private final RestClient restClient;

    /**
     * Base URL of search-service.
     *
     * In k8s, the default "http://search-service:8084" will work (service DNS).
     * For local dev (boot run), you can override with:
     *   gocommerce.search.base-url=http://localhost:8084
     */
    public SearchIndexClient(@Value("${gocommerce.search.base-url:http://search-service:8084}") String baseUrl,
                             @Value("${gocommerce.search.connect-timeout:2s}") Duration connectTimeout,
                             @Value("${gocommerce.search.read-timeout:3s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @CircuitBreaker(name = "searchIndex", fallbackMethod = "indexProductFallback")
    @Retry(name = "searchIndex")
    public void indexProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }

        Map<String, Object> payload = toPayload(product);

        restClient.post()
                .uri("/api/v1/search/index-product")
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        log.info("Indexed product {} in search-service", product.getId());
    }

    @CircuitBreaker(name = "searchIndex", fallbackMethod = "deleteProductFallback")
    @Retry(name = "searchIndex")
    public void deleteProduct(Long productId) {
        if (productId == null) {
            return;
        }

        restClient.delete()
                .uri("/api/v1/search/products/{id}", String.valueOf(productId))
                .retrieve()
                .toBodilessEntity();

        log.info("Deleted product {} from search index", productId);
    }

    // ---- Circuit breaker fallbacks ----

    @SuppressWarnings("unused")
    private void indexProductFallback(Product product, Throwable ex) {
        Long id = (product != null ? product.getId() : null);
        log.warn("Circuit breaker fallback while indexing product {} in search-service", id, ex);
        // We intentionally swallow the error so admin product flow still succeeds.
    }

    @SuppressWarnings("unused")
    private void deleteProductFallback(Long productId, Throwable ex) {
        log.warn("Circuit breaker fallback while deleting product {} from search index", productId, ex);
        // Same: do not break admin flows if search-service is down.
    }

    private Map<String, Object> toPayload(Product p) {
        Map<String, Object> map = new HashMap<>();
        // Keys chosen to match SearchService.toDocument(...)
        map.put("id", String.valueOf(p.getId()));
        map.put("slug", p.getSlug());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("brand", p.getBrand());
        map.put("category", p.getCategorySlug());
        map.put("price", p.getPrice());
        map.put("currency", p.getCurrency());
        map.put("stockQuantity", p.getStockQuantity());

        List<String> imageUrls = p.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            map.put("thumbnailUrl", imageUrls.get(0));
            map.put("imageUrls", imageUrls);
        }

        Map<String, String> attributes = p.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            map.put("attributes", attributes);
        }
        map.put("tags", Stream.of(p.getBrand(), p.getCategorySlug())
                .filter(value -> value != null && !value.isBlank())
                .toList());
        map.put("embeddingText", buildEmbeddingText(p));

        return map;
    }

    private String buildEmbeddingText(Product product) {
        StringJoiner joiner = new StringJoiner(" ");
        addText(joiner, product.getName());
        addText(joiner, product.getDescription());
        addText(joiner, product.getBrand());
        addText(joiner, product.getCategorySlug());

        Map<String, String> attributes = product.getAttributes();
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                addText(joiner, key);
                addText(joiner, value);
            });
        }

        return joiner.toString();
    }

    private void addText(StringJoiner joiner, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(value);
        }
    }
}
