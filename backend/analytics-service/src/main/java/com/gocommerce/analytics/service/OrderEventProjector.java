package com.gocommerce.analytics.service;

import com.gocommerce.analytics.events.OrderCreatedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderEventProjector {
    private final JdbcTemplate jdbc;
    private final com.gocommerce.analytics.metrics.AnalyticsMetrics metrics;
    public OrderEventProjector(JdbcTemplate jdbc, com.gocommerce.analytics.metrics.AnalyticsMetrics metrics) {
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
        jdbc.update("""
                INSERT INTO analytics_summary (id, total_orders, total_revenue) VALUES (1, 1, ?)
                ON CONFLICT (id) DO UPDATE SET total_orders = analytics_summary.total_orders + 1,
                    total_revenue = analytics_summary.total_revenue + EXCLUDED.total_revenue
                """, event.totalAmount());
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { metrics.onOrderRecorded(event.totalAmount()); }
                });
    }
}
