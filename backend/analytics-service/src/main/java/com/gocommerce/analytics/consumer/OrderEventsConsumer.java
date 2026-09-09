package com.gocommerce.analytics.consumer;

import com.gocommerce.analytics.events.OrderCreatedEvent;
import com.gocommerce.analytics.service.OrderEventProjector;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsConsumer {

    private final OrderEventProjector analyticsService;

    public OrderEventsConsumer(OrderEventProjector analyticsService) {
        this.analyticsService = analyticsService;
    }

    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderCreatedEvent event) {
        analyticsService.recordOrder(event);
    }
}
