package com.gocommerce.orders.service;

import com.gocommerce.orders.dto.OrderDtos.CreateOrderItemRequest;
import com.gocommerce.orders.dto.OrderDtos.CreateOrderRequest;
import com.gocommerce.orders.dto.OrderDtos.OrderItemResponse;
import com.gocommerce.orders.dto.OrderDtos.OrderResponse;
import com.gocommerce.orders.events.OrderCreatedEvent;
import com.gocommerce.orders.events.OrderEventsProducer;
import com.gocommerce.orders.model.Order;
import com.gocommerce.orders.model.OrderItem;
import com.gocommerce.orders.model.OrderStatus;
import com.gocommerce.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventsProducer orderEventsProducer; // 👈 NEW

    public OrderService(OrderRepository orderRepository,
                        OrderEventsProducer orderEventsProducer) {
        this.orderRepository = orderRepository;
        this.orderEventsProducer = orderEventsProducer;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        BigDecimal total = request.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(
                request.userId(),
                OrderStatus.PAID, // for now assume paid; later integrate payments
                total
        );

        for (CreateOrderItemRequest itemReq : request.items()) {
            OrderItem item = new OrderItem(
                    itemReq.productId(),
                    itemReq.productName(),
                    itemReq.quantity(),
                    itemReq.unitPrice()
            );
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);

        // 👇 Build and publish Kafka event AFTER successful save
        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId().toString(),
                saved.getUserId(),
                saved.getTotalAmount(),
                saved.getStatus().name(),
                saved.getItems().stream()
                        .map(i -> new OrderCreatedEvent.Line(
                                i.getProductId(),
                                i.getProductName(),
                                i.getQuantity(),
                                i.getUnitPrice()
                        ))
                        .toList()
        );
        orderEventsProducer.publishOrderCreated(event);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrdersForUser(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(),
                        i.getProductId(),
                        i.getProductName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getLineTotal()
                ))
                .toList();

        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            itemResponses
        );
    }
}
