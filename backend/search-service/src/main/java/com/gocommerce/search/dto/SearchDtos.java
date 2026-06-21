package com.gocommerce.search.dto;

import java.math.BigDecimal;
import java.util.List;

public class SearchDtos {

    public record SearchRequest(
            String query,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            String color,
            String type,
            String fit,
            String storage,
            String memory,
            String material,
            String sort,
            String mode,
            Integer page,
            Integer size
    ) {
        public SearchRequest(String query, String category, Integer page, Integer size) {
            this(query, category, null, null, null, null, null, null, null, null, null, null,
                    null, null, page, size);
        }

        public SearchRequest(String query, String category, String mode, Integer page, Integer size) {
            this(query, category, null, null, null, null, null, null, null, null, null, null,
                    null, mode, page, size);
        }

        public SearchRequest(String query,
                             String category,
                             String brand,
                             BigDecimal minPrice,
                             BigDecimal maxPrice,
                             Boolean inStock,
                             String color,
                             String type,
                             String fit,
                             String storage,
                             String memory,
                             String material,
                             String sort,
                             Integer page,
                             Integer size) {
            this(query, category, brand, minPrice, maxPrice, inStock, color, type, fit, storage, memory,
                    material, sort, null, page, size);
        }
    }

    public record SearchResultItem(
            String id,
            String slug,
            String name,
            String category,
            BigDecimal price,
            String currency,
            String thumbnailUrl
    ) {}

    public record SearchResponse(
            List<SearchResultItem> items,
            long total,
            int page,
            int size,
            int totalPages
    ) {
        public SearchResponse(List<SearchResultItem> items, long total) {
            this(items, total, 0, items != null ? items.size() : 0,
                    items == null || items.isEmpty() ? 0 : 1);
        }
    }
}
