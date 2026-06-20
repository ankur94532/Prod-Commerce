package com.gocommerce.orders.web;

import com.gocommerce.orders.OrderServiceApplication;
import com.gocommerce.orders.dto.OrderDtos.OrderItemResponse;
import com.gocommerce.orders.dto.OrderDtos.OrderResponse;
import com.gocommerce.orders.events.OrderEventsProducer;
import com.gocommerce.orders.outbox.OutboxEventRepository;
import com.gocommerce.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OrderServiceApplication.class)
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderEventsProducer orderEventsProducer;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/orders/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("order-service:OK")));
    }

    @Test
    @WithMockUser(username = "user-123", roles = { "CUSTOMER" })
    void createOrder_returnsCreatedOrder_andPassesIdempotencyKey() throws Exception {
        when(orderService.createOrder(any(), eq("idem-1"))).thenReturn(new OrderResponse(
                1L,
                "user-123",
                "PAID",
                new BigDecimal("200.00"),
                Instant.now(),
                List.of(new OrderItemResponse(10L, "1", "Catalog Product", 2, new BigDecimal("100.00"), new BigDecimal("200.00")))
        ));

        String json = """
                {
                  "userId": "user-123",
                  "items": [
                    {
                      "productId": "1",
                      "productName": "Tampered Client Name",
                      "quantity": 2,
                      "unitPrice": 1.00
                    }
                  ],
                  "payment": {
                    "cardNumber": "4242424242424242",
                    "cardExpiry": "12/30",
                    "cardCvc": "123"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.totalAmount").value(200))
                .andExpect(jsonPath("$.items[0].productName").value("Catalog Product"));
    }

    @Test
    @WithMockUser(username = "user-123", roles = { "CUSTOMER" })
    void createOrder_forDifferentUserId_returnsForbidden() throws Exception {
        String json = """
                {
                  "userId": "other-user",
                  "items": [
                    {
                      "productId": "1",
                      "quantity": 1
                    }
                  ],
                  "payment": {
                    "cardNumber": "4242424242424242",
                    "cardExpiry": "12/30",
                    "cardCvc": "123"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user-123", roles = { "CUSTOMER" })
    void getUserOrders_returnsOrdersForAuthenticatedUser() throws Exception {
        when(orderService.listOrdersForUser("user-123")).thenReturn(List.of(new OrderResponse(
                1L,
                "user-123",
                "PAID",
                new BigDecimal("200.00"),
                Instant.now(),
                List.of(new OrderItemResponse(10L, "1", "Product 1", 2, new BigDecimal("100.00"), new BigDecimal("200.00")))
        )));

        mockMvc.perform(get("/api/v1/orders")
                        .param("userId", "user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].userId").value("user-123"))
                .andExpect(jsonPath("$[0].items[0].productId").value("1"));
    }

    @Test
    @WithMockUser(username = "user-123", roles = { "CUSTOMER" })
    void getUserOrders_forDifferentUserId_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("userId", "someone-else"))
                .andExpect(status().isForbidden());
    }
}
