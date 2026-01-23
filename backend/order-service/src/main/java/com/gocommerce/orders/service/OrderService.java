package com.gocommerce.orders.service;

import com.gocommerce.orders.dto.OrderDtos.CreateOrderItemRequest;
import com.gocommerce.orders.dto.OrderDtos.CreateOrderRequest;
import com.gocommerce.orders.dto.OrderDtos.OrderItemResponse;
import com.gocommerce.orders.dto.OrderDtos.OrderResponse;
import com.gocommerce.orders.events.OrderCreatedEvent;
import com.gocommerce.orders.events.OrderEventsProducer;
import com.gocommerce.orders.metrics.OrderMetrics;
import com.gocommerce.orders.model.Order;
import com.gocommerce.orders.model.OrderItem;
import com.gocommerce.orders.model.OrderStatus;
import com.gocommerce.orders.payment.PaymentChargeRequest;
import com.gocommerce.orders.payment.PaymentProvider;
import com.gocommerce.orders.payment.PaymentResult;
import com.gocommerce.orders.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventsProducer orderEventsProducer;
    private final OrderMetrics orderMetrics;
    private final PaymentProvider paymentProvider;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        OrderEventsProducer orderEventsProducer,
                        OrderMetrics orderMetrics,
                        PaymentProvider paymentProvider) {
        this.orderRepository = orderRepository;
        this.orderEventsProducer = orderEventsProducer;
        this.orderMetrics = orderMetrics;
        this.paymentProvider = paymentProvider;
    }

    // kept for existing tests (2-arg ctor)
    public OrderService(OrderRepository orderRepository,
                        OrderEventsProducer orderEventsProducer) {
        this(orderRepository, orderEventsProducer, null, req -> PaymentResult.success("test", "test-tx"));
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        BigDecimal total = request.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (orderMetrics != null) {
            orderMetrics.onOrderCreated();
            orderMetrics.recordOrderValue(total);
        }

        // ---------- Mock Stripe payment before persisting order ----------
        if (paymentProvider != null) {
            String cardNumber = request.payment() != null ? request.payment().cardNumber() : null;

            PaymentChargeRequest chargeRequest = new PaymentChargeRequest(
                    total,
                    "INR", // hardcoded for now; could be field later
                    cardNumber,
                    "Order for user " + request.userId()
            );

            PaymentResult result = paymentProvider.charge(chargeRequest);
            if (!result.success()) {
                // In real life you'd have a proper error type + 402 mapping. For now:
                throw new IllegalStateException("Payment failed: " + result.failureReason());
            }
        }

        // ---------- Create and save order ----------
        Order order = new Order(
                request.userId(),
                OrderStatus.PAID,
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

        if (orderMetrics != null) {
            orderMetrics.onOrderCompleted();
        }

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
