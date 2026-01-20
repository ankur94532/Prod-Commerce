package com.gocommerce.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "analytics_summary")
public class AnalyticsSummary {

    @Id
    private Long id; // always 1

    @Column(nullable = false)
    private long totalOrders;

    @Column(nullable = false)
    private BigDecimal totalRevenue;

    protected AnalyticsSummary() {}

    public static AnalyticsSummary initial() {
        AnalyticsSummary s = new AnalyticsSummary();
        s.id = 1L;
        s.totalOrders = 0;
        s.totalRevenue = BigDecimal.ZERO;
        return s;
    }

    public Long getId() { return id; }
    public long getTotalOrders() { return totalOrders; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }

    public void incrementOrder(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        this.totalOrders++;
        this.totalRevenue = this.totalRevenue.add(amount);
    }
}
