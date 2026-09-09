package com.gocommerce.recommendation.service;

import com.gocommerce.recommendation.events.OrderCreatedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderEventProjector {
    private final JdbcTemplate jdbc;
    private final com.gocommerce.recommendation.metrics.RecommendationMetrics metrics;
    public OrderEventProjector(JdbcTemplate jdbc, com.gocommerce.recommendation.metrics.RecommendationMetrics metrics) {
        this.jdbc = jdbc; this.metrics = metrics;
    }

    @Transactional
    public void recordOrder(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null || event.orderId().isBlank()
                || !"PAID".equals(event.status()) || event.totalAmount() == null
                || event.totalAmount().signum() < 0 || event.items() == null) {
            throw new IllegalArgumentException("Invalid paid order event");
        }
        for (var line : event.items()) {
            if (line == null || line.productId() == null || line.productId().isBlank()
                    || line.productName() == null || line.quantity() <= 0 || line.unitPrice() == null
                    || line.unitPrice().signum() < 0) throw new IllegalArgumentException("Invalid order line");
        }
        int inserted = jdbc.update("""
                INSERT INTO processed_order_events (order_id) VALUES (?)
                ON CONFLICT (order_id) DO NOTHING
                """, event.orderId());
        if (inserted == 0) return;
        // Stable lock order also avoids deadlocks between different multi-product orders.
        for (var line : event.items().stream().sorted(java.util.Comparator.comparing(OrderCreatedEvent.Line::productId)).toList()) {
            jdbc.update("""
                    INSERT INTO product_stats (product_id, product_name, total_quantity, total_revenue)
                    VALUES (?, ?, ?, ?) ON CONFLICT (product_id) DO UPDATE SET
                      product_name = EXCLUDED.product_name,
                      total_quantity = product_stats.total_quantity + EXCLUDED.total_quantity,
                      total_revenue = product_stats.total_revenue + EXCLUDED.total_revenue
                    """, line.productId(), line.productName(), line.quantity(),
                    line.unitPrice().multiply(java.math.BigDecimal.valueOf(line.quantity())));
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { metrics.onOrderEventProcessed(); }
                });
    }
}
