package com.gocommerce.recommendation.events;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(
        String orderId,
        String userId,
        BigDecimal totalAmount,
        String status,
        List<Line> items
) {
    public record Line(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
