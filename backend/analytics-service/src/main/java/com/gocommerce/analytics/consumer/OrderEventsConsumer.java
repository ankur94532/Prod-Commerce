package com.gocommerce.analytics.consumer;

import com.gocommerce.analytics.events.OrderCreatedEvent;
import com.gocommerce.analytics.service.AnalyticsService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsConsumer {

    private static final Log log = LogFactory.getLog(OrderEventsConsumer.class);

    private final AnalyticsService analyticsService;

    public OrderEventsConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order.created event for orderId=" + event.orderId()
                + ", totalAmount=" + event.totalAmount());

        analyticsService.recordOrder(event.totalAmount());
    }
}
