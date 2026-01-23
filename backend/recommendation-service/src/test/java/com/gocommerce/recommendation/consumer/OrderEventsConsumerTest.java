package com.gocommerce.recommendation.consumer;

import com.gocommerce.recommendation.events.OrderCreatedEvent;
import com.gocommerce.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class OrderEventsConsumerTest {

    private static class RecordingRecommendationService extends RecommendationService {
        OrderCreatedEvent lastEvent;

        RecordingRecommendationService() {
            super(null); // repository not used in this stub
        }

        @Override
        public void recordOrder(OrderCreatedEvent event) {
            this.lastEvent = event;
        }
    }

    @Test
    void handleOrderCreated_delegatesToService() {
        RecordingRecommendationService stubService = new RecordingRecommendationService();
        OrderEventsConsumer consumer = new OrderEventsConsumer(stubService);

        OrderCreatedEvent.Line line = new OrderCreatedEvent.Line(
                "p1",
                "Product One",
                1,
                new BigDecimal("100.00")
        );
        OrderCreatedEvent event = new OrderCreatedEvent(
                "order-1",
                "user-1",
                new BigDecimal("100.00"),
                "PAID",
                List.of(line)
        );

        consumer.handleOrderCreated(event);

        // verify delegation by checking the stub was called with the same event
        assertSame(event, stubService.lastEvent);
    }
}
