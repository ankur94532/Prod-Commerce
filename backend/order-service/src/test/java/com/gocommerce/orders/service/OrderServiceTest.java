package com.gocommerce.orders.service;

import com.gocommerce.orders.dto.OrderDtos.CreateOrderItemRequest;
import com.gocommerce.orders.dto.OrderDtos.CreateOrderRequest;
import com.gocommerce.orders.dto.OrderDtos.OrderResponse;
import com.gocommerce.orders.dto.OrderDtos.PaymentDetails;
import com.gocommerce.orders.events.OrderCreatedEvent;
import com.gocommerce.orders.events.OrderEventsProducer;
import com.gocommerce.orders.model.Order;
import com.gocommerce.orders.model.OrderItem;
import com.gocommerce.orders.model.OrderStatus;
import com.gocommerce.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventsProducer orderEventsProducer;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // uses the 2-arg ctor that delegates to the full one (metrics/payment/etc. can be null)
        orderService = new OrderService(orderRepository, orderEventsProducer);
    }

    @Test
    void createOrder_persistsOrder_andPublishesEvent() {
        // given
        CreateOrderItemRequest item1 = new CreateOrderItemRequest(
                "p1", "Product 1", 2, new BigDecimal("100.00"));
        CreateOrderItemRequest item2 = new CreateOrderItemRequest(
                "p2", "Product 2", 1, new BigDecimal("50.00"));

        PaymentDetails payment = new PaymentDetails(
                "4242424242424242",
                "12/30",
                "123"
        );

        CreateOrderRequest request = new CreateOrderRequest(
                "user-123",
                List.of(item1, item2),
                payment
        );

        // repository will assign an id
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                ReflectionTestUtils.setField(o, "id", 10L);
            }
            return o;
        });

        // when
        OrderResponse response = orderService.createOrder(request);

        // then: totals & mapping
        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("user-123", response.userId());
        assertEquals(OrderStatus.PAID.name(), response.status());
        // 2 * 100 + 1 * 50 = 250
        assertEquals(0, response.totalAmount().compareTo(new BigDecimal("250.00")));
        assertEquals(2, response.items().size());

        // then: event published with correct data
        ArgumentCaptor<OrderCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(orderEventsProducer).publishOrderCreated(eventCaptor.capture());

        OrderCreatedEvent event = eventCaptor.getValue();
        assertEquals("10", event.orderId());
        assertEquals("user-123", event.userId());
        assertEquals(0, event.totalAmount().compareTo(new BigDecimal("250.00")));
        assertEquals(2, event.items().size());
        assertEquals("p1", event.items().get(0).productId());
    }

    @Test
    void listOrdersForUser_mapsEntitiesToResponses() {
        // given
        Order order = new Order("user-123", OrderStatus.PAID, new BigDecimal("200.00"));
        OrderItem item = new OrderItem("p1", "Product 1", 2, new BigDecimal("100.00"));
        order.addItem(item);
        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-123"))
                .thenReturn(List.of(order));

        // when
        List<OrderResponse> responses = orderService.listOrdersForUser("user-123");

        // then
        assertEquals(1, responses.size());
        OrderResponse resp = responses.get(0);
        assertEquals(1L, resp.id());
        assertEquals("user-123", resp.userId());
        assertEquals(0, resp.totalAmount().compareTo(new BigDecimal("200.00")));
        assertEquals(1, resp.items().size());
        assertEquals("p1", resp.items().get(0).productId());
    }
}
