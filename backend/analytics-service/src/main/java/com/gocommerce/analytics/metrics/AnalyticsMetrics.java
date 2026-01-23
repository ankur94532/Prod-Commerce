package com.gocommerce.analytics.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AnalyticsMetrics {

    private final Counter ordersRecorded;
    private final Counter summaryRequests;
    private final DistributionSummary orderAmount;

    public AnalyticsMetrics(MeterRegistry registry) {
        this.ordersRecorded = Counter.builder("analytics_orders_recorded_total")
                .description("Orders recorded into analytics summary")
                .tag("service", "analytics-service")
                .register(registry);

        this.summaryRequests = Counter.builder("analytics_summary_requests_total")
                .description("Requests to /analytics/summary")
                .tag("service", "analytics-service")
                .register(registry);

        this.orderAmount = DistributionSummary.builder("analytics_order_amount")
                .description("Distribution of order amounts recorded")
                .baseUnit("INR")
                .tag("service", "analytics-service")
                .register(registry);
    }

    public void onOrderRecorded(BigDecimal amount) {
        ordersRecorded.increment();
        if (amount != null) {
            orderAmount.record(amount.doubleValue());
        }
    }

    public void onSummaryRequested() {
        summaryRequests.increment();
    }
}
