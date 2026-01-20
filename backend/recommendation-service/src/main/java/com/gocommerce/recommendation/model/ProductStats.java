package com.gocommerce.recommendation.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "product_stats",
        indexes = {
                @Index(name = "idx_product_stats_product_id", columnList = "productId", unique = true)
        }
)
public class ProductStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String productId;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private long totalQuantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalRevenue;

    protected ProductStats() {
    }

    public ProductStats(String productId,
                        String productName,
                        long totalQuantity,
                        BigDecimal totalRevenue) {
        this.productId = productId;
        this.productName = productName;
        this.totalQuantity = totalQuantity;
        this.totalRevenue = totalRevenue;
    }

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void addPurchase(long quantity, BigDecimal lineTotal, String latestName) {
        if (lineTotal == null) lineTotal = BigDecimal.ZERO;
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        this.totalQuantity += quantity;
        this.totalRevenue = this.totalRevenue.add(lineTotal);

        // keep latest name in case catalog changed
        if (latestName != null && !latestName.isBlank()) {
            this.productName = latestName;
        }
    }
}
