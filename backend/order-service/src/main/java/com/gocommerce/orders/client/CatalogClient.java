package com.gocommerce.orders.client;

@FunctionalInterface
public interface CatalogClient {
    void decrementStock(String productId, int quantity);
}
