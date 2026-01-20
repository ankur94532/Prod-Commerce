package com.gocommerce.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

public class RecommendationDtos {

    public record TrendingProduct(
            String productId,
            String productName,
            long totalQuantity,
            BigDecimal totalRevenue
    ) {}

    public record TrendingResponse(
            List<TrendingProduct> items
    ) {}
}
