package com.gocommerce.recommendation.service;

import java.math.BigDecimal;
import java.util.List;

public interface CatalogFallbackClient {

    List<CatalogProduct> fetchProducts(int limit);

    record CatalogProduct(String productId, String productName, BigDecimal price, String categorySlug) {
        public CatalogProduct(String productId, String productName, BigDecimal price) {
            this(productId, productName, price, null);
        }
    }
}
