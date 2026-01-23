package com.gocommerce.orders.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderMetrics {

    private final Counter orderCreated;
    private final Counter orderFailed;
    private final Counter paymentFailed;
    private final Counter orderCompleted;
    private final DistributionSummary orderValue;

    public OrderMetrics(MeterRegistry registry) {
        this.orderCreated = Counter.builder("order_created_total")
                .description("Orders created (attempted)")
                .tag("service", "order-service")
                .register(registry);

        this.orderFailed = Counter.builder("order_failed_total")
                .description("Orders that failed validation/business rules")
                .tag("service", "order-service")
                .register(registry);

        this.paymentFailed = Counter.builder("order_payment_failed_total")
                .description("Orders where payment failed")
                .tag("service", "order-service")
                .register(registry);

        this.orderCompleted = Counter.builder("order_completed_total")
                .description("Orders successfully placed and confirmed")
                .tag("service", "order-service")
                .register(registry);

        this.orderValue = DistributionSummary.builder("order_value_amount")
                .description("Order value distribution")
                .baseUnit("INR")
                .tag("service", "order-service")
                .register(registry);
    }

    public void onOrderCreated() { orderCreated.increment(); }

    public void onOrderFailed() { orderFailed.increment(); }

    public void onPaymentFailed() { paymentFailed.increment(); }

    public void onOrderCompleted() { orderCompleted.increment(); }

    public void recordOrderValue(BigDecimal amount) {
        if (amount != null) {
            orderValue.record(amount.doubleValue());
        }
    }
}
