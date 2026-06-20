package com.gocommerce.orders.client;

import java.math.BigDecimal;

public interface CatalogClient {

    ProductSnapshot getProductSnapshot(String productId);

    void decrementStock(String productId, int quantity);

    void incrementStock(String productId, int quantity);

    record ProductSnapshot(
            String productId,
            String productName,
            BigDecimal unitPrice,
            String currency
    ) {}
}
