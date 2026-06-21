package com.gocommerce.search.client;

import com.gocommerce.search.config.CatalogProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    public static final int DEFAULT_PAGE_SIZE = 200;

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CatalogClient(RestTemplate restTemplate, CatalogProperties props) {
        this.restTemplate = restTemplate;
        this.baseUrl = props.getBaseUrl();
    }

    @CircuitBreaker(name = "catalogClient", fallbackMethod = "fetchAllProductsFallback")
    @Retry(name = "catalogClient")
    public List<Map<String, Object>> fetchAllProducts() {
        int page = 0;
        int size = DEFAULT_PAGE_SIZE;
        int totalPages = 1;
        java.util.ArrayList<Map<String, Object>> products = new java.util.ArrayList<>();

        do {
            CatalogProductPage productPage = fetchProductsPage(page, size);
            products.addAll(productPage.products());
            totalPages = productPage.totalPages();
            page++;
        } while (page < totalPages);

        log.info("Fetched {} products from catalog across {} page(s)", products.size(), totalPages);
        return products;
    }

    @CircuitBreaker(name = "catalogClient", fallbackMethod = "fetchProductsPageFallback")
    @Retry(name = "catalogClient")
    public CatalogProductPage fetchProductsPage(int page, int size) {
        int requestedPage = Math.max(page, 0);
        int requestedSize = Math.max(size, 1);
        String url = baseUrl + "/api/v1/products?page=" + requestedPage + "&size=" + requestedSize;
        log.info("Fetching products from catalog at {}", url);

        Object body = fetchBody(url);
        List<Map<String, Object>> products = extractProducts(body);
        int totalPages = extractTotalPages(body, requestedPage + 1);
        long totalElements = extractTotalElements(body, products.size());
        return new CatalogProductPage(products, requestedPage, requestedSize, totalPages, totalElements);
    }

    private Object fetchBody(String url) {
        ResponseEntity<Object> resp = restTemplate.exchange(
                url, HttpMethod.GET, null, Object.class);

        Object body = resp.getBody();
        if (body == null) {
            log.warn("Catalog returned null body for {}", url);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractProducts(Object body) {
        if (body == null) {
            return List.of();
        }
        // Case 1: plain list of products: [ {..}, {..} ]
        if (body instanceof List<?> list) {
            log.info("Catalog /products returned list with {} items", list.size());
            return (List<Map<String, Object>>) (List<?>) list;
        }

        // Case 2: wrapper object (paged or envelope), e.g. { content: [..], items: [..], ... }
        if (body instanceof Map<?, ?> map) {
            Object items = map.get("items");
            if (!(items instanceof List<?>)) {
                items = map.get("content");
            }

            // Fallback: any List value in the map
            if (!(items instanceof List<?>)) {
                for (Object v : map.values()) {
                    if (v instanceof List<?> l) {
                        items = l;
                        break;
                    }
                }
            }

            if (items instanceof List<?> list) {
                log.info("Catalog /products returned wrapper with list of {} items", list.size());
                return (List<Map<String, Object>>) (List<?>) list;
            }

            log.warn("Catalog /products returned map but no list field found: keys={}", map.keySet());
            return List.of();
        }

        log.warn("Catalog /products returned unexpected type: {}", body.getClass());
        return List.of();
    }

    private int extractTotalPages(Object body, int fallback) {
        if (body instanceof Map<?, ?> map) {
            Object totalPages = map.get("totalPages");
            if (totalPages instanceof Number n) {
                return Math.max(n.intValue(), 1);
            }
            if (totalPages != null) {
                try {
                    return Math.max(Integer.parseInt(String.valueOf(totalPages)), 1);
                } catch (NumberFormatException ex) {
                    log.warn("Catalog returned non-numeric totalPages={}", totalPages);
                }
            }
        }
        return fallback;
    }

    private long extractTotalElements(Object body, long fallback) {
        if (body instanceof Map<?, ?> map) {
            Object totalElements = map.get("totalElements");
            if (totalElements instanceof Number n) {
                return Math.max(n.longValue(), 0L);
            }
            if (totalElements != null) {
                try {
                    return Math.max(Long.parseLong(String.valueOf(totalElements)), 0L);
                } catch (NumberFormatException ex) {
                    log.warn("Catalog returned non-numeric totalElements={}", totalElements);
                }
            }
        }
        return fallback;
    }

    @SuppressWarnings("unused")
    public List<Map<String, Object>> fetchAllProductsFallback(Throwable ex) {
        log.warn("CatalogClient.fetchAllProducts fallback triggered, returning empty list", ex);
        return List.of();
    }

    @SuppressWarnings("unused")
    public CatalogProductPage fetchProductsPageFallback(int page, int size, Throwable ex) {
        log.warn("CatalogClient.fetchProductsPage fallback triggered for page={}, size={}, returning empty page",
                page, size, ex);
        return new CatalogProductPage(List.of(), Math.max(page, 0), Math.max(size, 1), Math.max(page + 1, 1), 0L);
    }

    public record CatalogProductPage(
            List<Map<String, Object>> products,
            int page,
            int size,
            int totalPages,
            long totalElements
    ) {
    }
}
