package com.gocommerce.orders.events;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OrderEventsProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventsProducer(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        // key = orderId so events for same order go to same partition.
        // Wait for broker ack so outbox rows are marked published only after Kafka accepted the event.
        try {
            kafkaTemplate.send("order.created", event.orderId(), event).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish order.created event for order " + event.orderId(), e);
        }
    }
}
