package com.gocommerce.analytics.dto;

import java.math.BigDecimal;

public record AnalyticsSummaryResponse(
        long totalOrders,
        BigDecimal totalRevenue
) {
}
