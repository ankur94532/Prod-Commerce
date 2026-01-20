package com.gocommerce.recommendation.consumer;

import com.gocommerce.recommendation.events.OrderCreatedEvent;
import com.gocommerce.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventsConsumerTest {

    @Mock
    private RecommendationService recommendationService;

    @InjectMocks
    private OrderEventsConsumer consumer;

    @Test
    void handleOrderCreated_delegatesToService() {
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

        verify(recommendationService).recordOrder(event);
    }
}
