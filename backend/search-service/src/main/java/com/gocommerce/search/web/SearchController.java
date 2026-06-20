package com.gocommerce.search.web;

import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import com.gocommerce.search.service.SearchService;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/health")
    public String health() {
        return "search-service:OK";
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "inStock", required = false) Boolean inStock,
            @RequestParam(name = "color", required = false) String color,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "fit", required = false) String fit,
            @RequestParam(name = "storage", required = false) String storage,
            @RequestParam(name = "memory", required = false) String memory,
            @RequestParam(name = "material", required = false) String material,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size) {
        logger.info("Search request: q='{}', category='{}', brand='{}', minPrice={}, maxPrice={}, inStock={}, color='{}', type='{}', fit='{}', storage='{}', memory='{}', material='{}', sort='{}', page={}, size={}",
                query, category, brand, minPrice, maxPrice, inStock, color, type, fit, storage, memory, material, sort, page, size);
        SearchRequest req = new SearchRequest(query, category, brand, minPrice, maxPrice, inStock,
                color, type, fit, storage, memory, material, sort, page, size);
        return searchService.search(req);
    }

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        int indexed = searchService.reindexProducts();
        return Map.of("indexed", indexed);
    }

    // 🔹 NEW: index a single product (called from catalog-service)
    @PostMapping("/index-product")
    public ResponseEntity<Map<String, Object>> indexProduct(@RequestBody Map<String, Object> payload) {
        searchService.indexProductFromPayload(payload);
        return ResponseEntity.ok(Map.of("id", payload.get("id")));
    }

    // 🔹 NEW: remove single product from index
    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        searchService.deleteProductFromIndex(id);
        return ResponseEntity.noContent().build();
    }
}
