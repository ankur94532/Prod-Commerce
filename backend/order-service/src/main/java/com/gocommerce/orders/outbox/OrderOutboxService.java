package com.gocommerce.orders.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.orders.events.OrderCreatedEvent;
import com.gocommerce.orders.model.Order;
import org.springframework.stereotype.Service;

@Service
public class OrderOutboxService {

    public static final String ORDER_CREATED = "order.created";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderOutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueueOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId().toString(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getItems().stream()
                        .map(i -> new OrderCreatedEvent.Line(
                                i.getProductId(),
                                i.getProductName(),
                                i.getQuantity(),
                                i.getUnitPrice()
                        ))
                        .toList()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new OutboxEvent("ORDER", order.getId().toString(), ORDER_CREATED, payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order-created outbox event", e);
        }
    }
}
