package com.gocommerce.orders.service;

import com.gocommerce.orders.client.CatalogClient;
import com.gocommerce.orders.client.CatalogClient.ProductSnapshot;
import com.gocommerce.orders.dto.OrderDtos.CreateOrderItemRequest;
import com.gocommerce.orders.dto.OrderDtos.CreateOrderRequest;
import com.gocommerce.orders.dto.OrderDtos.OrderResponse;
import com.gocommerce.orders.dto.OrderDtos.PaymentDetails;
import com.gocommerce.orders.model.Order;
import com.gocommerce.orders.model.OrderItem;
import com.gocommerce.orders.model.OrderStatus;
import com.gocommerce.orders.outbox.OrderOutboxService;
import com.gocommerce.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxService orderOutboxService;

    @Mock
    private CatalogClient catalogClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderOutboxService, catalogClient);
    }

    @Test
    void createOrder_fetchesCatalogPrice_decrementsStock_andEnqueuesOutboxEvent() {
        CreateOrderItemRequest item1 = new CreateOrderItemRequest("1", "Tampered Product", 2, new BigDecimal("1.00"));
        CreateOrderItemRequest item2 = new CreateOrderItemRequest("2", "Tampered Product 2", 1, new BigDecimal("1.00"));
        PaymentDetails payment = new PaymentDetails("4242424242424242", "12/30", "123");
        CreateOrderRequest request = new CreateOrderRequest("user-123", List.of(item1, item2), payment);

        when(orderRepository.findByUserIdAndIdempotencyKey("user-123", "idem-1")).thenReturn(Optional.empty());
        when(catalogClient.getProductSnapshot("1"))
                .thenReturn(new ProductSnapshot("1", "Product 1", new BigDecimal("100.00"), "INR"));
        when(catalogClient.getProductSnapshot("2"))
                .thenReturn(new ProductSnapshot("2", "Product 2", new BigDecimal("50.00"), "INR"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                ReflectionTestUtils.setField(o, "id", 10L);
            }
            return o;
        });

        OrderResponse response = orderService.createOrder(request, "idem-1");

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("user-123", response.userId());
        assertEquals(OrderStatus.PAID.name(), response.status());
        assertEquals(0, response.totalAmount().compareTo(new BigDecimal("250.00")));
        assertEquals(2, response.items().size());
        assertEquals("Product 1", response.items().get(0).productName());
        assertEquals(0, response.items().get(0).unitPrice().compareTo(new BigDecimal("100.00")));

        verify(catalogClient).decrementStock("1", 2);
        verify(catalogClient).decrementStock("2", 1);
        verify(orderOutboxService).enqueueOrderCreated(any(Order.class));
    }

    @Test
    void createOrder_withSameIdempotencyKey_returnsExistingOrder() {
        Order existing = new Order("user-123", OrderStatus.PAID, new BigDecimal("200.00"), "INR", "idem-1");
        existing.addItem(new OrderItem("1", "Product 1", 2, new BigDecimal("100.00")));
        ReflectionTestUtils.setField(existing, "id", 99L);

        when(orderRepository.findByUserIdAndIdempotencyKey("user-123", "idem-1"))
                .thenReturn(Optional.of(existing));

        CreateOrderRequest request = new CreateOrderRequest(
                "user-123",
                List.of(new CreateOrderItemRequest("1", null, 2, null)),
                new PaymentDetails("4242424242424242", "12/30", "123")
        );

        OrderResponse response = orderService.createOrder(request, "idem-1");

        assertEquals(99L, response.id());
        assertEquals(0, response.totalAmount().compareTo(new BigDecimal("200.00")));
        verifyNoInteractions(catalogClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void listOrdersForUser_mapsEntitiesToResponses() {
        Order order = new Order("user-123", OrderStatus.PAID, new BigDecimal("200.00"));
        OrderItem item = new OrderItem("1", "Product 1", 2, new BigDecimal("100.00"));
        order.addItem(item);
        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-123"))
                .thenReturn(List.of(order));

        List<OrderResponse> responses = orderService.listOrdersForUser("user-123");

        assertEquals(1, responses.size());
        OrderResponse resp = responses.get(0);
        assertEquals(1L, resp.id());
        assertEquals("user-123", resp.userId());
        assertEquals(0, resp.totalAmount().compareTo(new BigDecimal("200.00")));
        assertEquals(1, resp.items().size());
        assertEquals("1", resp.items().get(0).productId());
    }
}
