package com.gocommerce.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

public class PopularityDtos {

    public record PopularityItem(
            String productId,
            long totalQuantity,
            BigDecimal totalRevenue
    ) {}

    public record PopularityResponse(
            List<PopularityItem> items
    ) {}
}
