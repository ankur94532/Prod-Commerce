package com.gocommerce.search.client;

import com.gocommerce.search.config.CatalogProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CatalogClient(RestTemplate restTemplate, CatalogProperties props) {
        this.restTemplate = restTemplate;
        this.baseUrl = props.getBaseUrl();
    }

    @CircuitBreaker(name = "catalogClient", fallbackMethod = "fetchAllProductsFallback")
    public List<Map<String, Object>> fetchAllProducts() {
        int page = 0;
        int size = 200;
        int totalPages = 1;
        java.util.ArrayList<Map<String, Object>> products = new java.util.ArrayList<>();

        do {
            String url = baseUrl + "/api/v1/products?page=" + page + "&size=" + size;
            log.info("Fetching products from catalog at {}", url);

            Object body = fetchBody(url);
            products.addAll(extractProducts(body));
            totalPages = extractTotalPages(body, totalPages);
            page++;
        } while (page < totalPages);

        log.info("Fetched {} products from catalog across {} page(s)", products.size(), totalPages);
        return products;
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

    @SuppressWarnings("unused")
    public List<Map<String, Object>> fetchAllProductsFallback(Throwable ex) {
        log.warn("CatalogClient.fetchAllProducts fallback triggered, returning empty list", ex);
        return List.of();
    }
}
