package com.gocommerce.orders.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.orders.OrderServiceApplication;
import com.gocommerce.orders.events.OrderEventsProducer;
import com.gocommerce.orders.model.Order;
import com.gocommerce.orders.model.OrderItem;
import com.gocommerce.orders.model.OrderStatus;
import com.gocommerce.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OrderServiceApplication.class)
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // We mock repository so DB is not used
    @MockBean
    private OrderRepository orderRepository;

    // And we mock events producer so tests don't touch Kafka
    @MockBean
    private OrderEventsProducer orderEventsProducer;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/orders/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("order-service:OK")));
    }

    @Test
    @WithMockUser(username = "user-123", roles = { "CUSTOMER" })
    void createOrder_returnsCreatedOrder() throws Exception {
        // When OrderService saves, simulate DB-generated ID so getId() is not null
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                ReflectionTestUtils.setField(o, "id", 1L);
            }
            return o;
        });

        String json = """
                {
                  "userId": "user-123",
                  "items": [
                    {
                      "productId": "p1",
                      "productName": "Test Product",
                      "quantity": 2,
                      "unitPrice": 100.00
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-123"))
                // 2 * 100 = 200
                .andExpect(jsonPath("$.totalAmount").value(200))
                .andExpect(jsonPath("$.items[0].productId").value("p1"));
    }

    @Test
    @WithMockUser(username = "user-123", roles = { "CUSTOMER" })
    void createOrder_forDifferentUserId_returnsForbidden() throws Exception {
        String json = """
                {
                  "userId": "other-user",
                  "items": [
                    {
                      "productId": "p1",
                      "productName": "Test Product",
                      "quantity": 1,
                      "unitPrice": 100.00
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
        Order order = new Order("user-123", OrderStatus.PAID, new BigDecimal("200.00"));
        OrderItem item = new OrderItem("p1", "Test Product", 2, new BigDecimal("100.00"));
        order.addItem(item);
        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-123"))
                .thenReturn(List.of(order));

        mockMvc.perform(get("/api/v1/orders")
                        .param("userId", "user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].userId").value("user-123"))
                .andExpect(jsonPath("$[0].items[0].productId").value("p1"));
    }

    @Test
    @WithMockUser(username = "user-123", roles = { "CUSTOMER" })
    void getUserOrders_forDifferentUserId_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("userId", "someone-else"))
                .andExpect(status().isForbidden());
    }
}
