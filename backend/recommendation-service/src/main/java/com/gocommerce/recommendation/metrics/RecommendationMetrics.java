package com.gocommerce.recommendation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMetrics {

    private final Counter ordersProcessed;
    private final Counter trendingRequests;

    public RecommendationMetrics(MeterRegistry registry) {
        this.ordersProcessed = Counter.builder("recommendation_orders_processed_total")
                .description("order.created events processed into stats")
                .tag("service", "recommendation-service")
                .register(registry);

        this.trendingRequests = Counter.builder("recommendation_trending_requests_total")
                .description("Calls to /recommendations/trending")
                .tag("service", "recommendation-service")
                .register(registry);
    }

    public void onOrderEventProcessed() {
        ordersProcessed.increment();
    }

    public void onTrendingRequested() {
        trendingRequests.increment();
    }
}
