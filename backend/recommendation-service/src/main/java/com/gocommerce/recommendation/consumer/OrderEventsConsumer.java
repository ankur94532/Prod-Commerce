package com.gocommerce.recommendation.consumer;

import com.gocommerce.recommendation.events.OrderCreatedEvent;
import com.gocommerce.recommendation.service.RecommendationService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsConsumer {

    private static final Log log = LogFactory.getLog(OrderEventsConsumer.class);

    private final RecommendationService recommendationService;

    public OrderEventsConsumer(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @KafkaListener(topics = "order.created", groupId = "recommendation-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("RecommendationService received order.created event, orderId=" + event.orderId());
        recommendationService.recordOrder(event);
    }
}
