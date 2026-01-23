package com.gocommerce.catalog.client;

import com.gocommerce.catalog.entity.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public SearchIndexClient(
            @Value("${gocommerce.search.base-url:http://search-service:8084}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @CircuitBreaker(name = "searchIndex", fallbackMethod = "indexProductFallback")
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
        map.put("category", p.getCategorySlug());
        map.put("price", p.getPrice());
        map.put("currency", p.getCurrency());

        List<String> imageUrls = p.getImageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            map.put("thumbnailUrl", imageUrls.get(0));
        }

        // You could also add tags (brand, category) if you want richer search later.
        return map;
    }
}
