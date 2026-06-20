package com.gocommerce.orders.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.orders.events.OrderCreatedEvent;
import com.gocommerce.orders.events.OrderEventsProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventsProducer orderEventsProducer;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           OrderEventsProducer orderEventsProducer,
                           ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderEventsProducer = orderEventsProducer;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${orders.outbox.publisher-delay-ms:5000}")
    @Transactional
    public void publishDueEvents() {
        for (OutboxEvent event : outboxEventRepository
                .findDueForPublishing(Instant.now()).stream().limit(50).toList()) {
            if (!OrderOutboxService.ORDER_CREATED.equals(event.getEventType())) {
                log.warn("Skipping unsupported outbox eventType={} id={}", event.getEventType(), event.getId());
                event.markFailedAttempt();
                continue;
            }

            try {
                OrderCreatedEvent orderCreatedEvent = objectMapper.readValue(event.getPayload(), OrderCreatedEvent.class);
                orderEventsProducer.publishOrderCreated(orderCreatedEvent);
                event.markPublished();
            } catch (Exception e) {
                log.warn("Failed to publish outbox event id={}, attempts={}", event.getId(), event.getAttempts() + 1, e);
                event.markFailedAttempt();
            }
        }
    }
}
