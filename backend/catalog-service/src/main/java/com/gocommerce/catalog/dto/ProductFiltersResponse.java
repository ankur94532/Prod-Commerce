package com.gocommerce.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductFiltersResponse(
        List<String> categories,
        List<String> brands,
        PriceRange price,
        Map<String, List<String>> attributes
) {
    public record PriceRange(BigDecimal min, BigDecimal max) {
    }
}
