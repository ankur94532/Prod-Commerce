package com.gocommerce.recommendation.consumer;

import com.gocommerce.recommendation.events.OrderCreatedEvent;
import com.gocommerce.recommendation.service.OrderEventProjector;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsConsumer {

    private final OrderEventProjector recommendationService;

    public OrderEventsConsumer(OrderEventProjector recommendationService) {
        this.recommendationService = recommendationService;
    }

    @KafkaListener(topics = "order.created", groupId = "recommendation-service")
    public void handleOrderCreated(OrderCreatedEvent event) {

        recommendationService.recordOrder(event);
    }
}
