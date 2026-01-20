package com.gocommerce.analytics.consumer;

import com.gocommerce.analytics.events.OrderCreatedEvent;
import com.gocommerce.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventsConsumerTest {

    // Simple stub that records calls instead of hitting a DB
    private static class StubAnalyticsService extends AnalyticsService {

        BigDecimal lastAmount;
        int calls;

        StubAnalyticsService() {
            super(null); // summaryRepository not used in this stub
        }

        @Override
        public void recordOrder(BigDecimal totalAmount) {
            this.lastAmount = totalAmount;
            this.calls++;
        }
    }

    @Test
    void handleOrderCreated_delegatesToAnalyticsService() {
        StubAnalyticsService analyticsService = new StubAnalyticsService();
        OrderEventsConsumer consumer = new OrderEventsConsumer(analyticsService);

        OrderCreatedEvent event = new OrderCreatedEvent(
                "order-1",
                "user-1",
                new BigDecimal("123.45"),
                "PAID",
                Collections.emptyList()
        );

        consumer.handleOrderCreated(event);

        assertThat(analyticsService.calls).isEqualTo(1);
        assertThat(analyticsService.lastAmount).isEqualByComparingTo("123.45");
    }
}
