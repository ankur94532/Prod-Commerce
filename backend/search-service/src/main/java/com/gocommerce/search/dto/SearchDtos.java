package com.gocommerce.search.dto;

import java.math.BigDecimal;
import java.util.List;

public class SearchDtos {

    public record SearchRequest(
            String query,
            String category,
            Integer page,
            Integer size
    ) {}

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
            long total
    ) {}
}
