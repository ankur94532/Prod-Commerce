package com.gocommerce.catalog.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CatalogMetrics {

    private final Counter listProducts;
    private final Counter productDetail;
    private final Counter productCreated;
    private final Counter productUpdated;
    private final Counter productDeleted;

    public CatalogMetrics(MeterRegistry registry) {
        this.listProducts = Counter.builder("catalog_list_requests_total")
                .description("Requests to list products")
                .tag("service", "catalog-service")
                .register(registry);

        this.productDetail = Counter.builder("catalog_detail_requests_total")
                .description("Requests for single product details")
                .tag("service", "catalog-service")
                .register(registry);

        this.productCreated = Counter.builder("catalog_product_created_total")
                .description("Products created")
                .tag("service", "catalog-service")
                .register(registry);

        this.productUpdated = Counter.builder("catalog_product_updated_total")
                .description("Products updated")
                .tag("service", "catalog-service")
                .register(registry);

        this.productDeleted = Counter.builder("catalog_product_deleted_total")
                .description("Products deleted")
                .tag("service", "catalog-service")
                .register(registry);
    }

    // Old name (if used somewhere)
    public void onListProducts() {
        listProducts.increment();
    }

    // New name used in ProductController
    public void onProductList() {
        listProducts.increment();
    }

    public void onProductDetail() {
        productDetail.increment();
    }

    public void onProductCreated() {
        productCreated.increment();
    }

    public void onProductUpdated() {
        productUpdated.increment();
    }

    public void onProductDeleted() {
        productDeleted.increment();
    }
}
