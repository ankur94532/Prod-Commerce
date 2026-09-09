package com.gocommerce.orders.service;

import com.gocommerce.orders.dto.OrderDtos.*;
import com.gocommerce.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    @Test void rejectsInvalidNestedItemsBeforeCreatingIntent() {
        var intents = mock(OrderIntentService.class);
        var service = new OrderService(intents, mock(OrderWorkflow.class), mock(OrderRepository.class));
        for (var items : List.of(List.of(new CreateOrderItemRequest("1", null, 0, null)),
                List.of(new CreateOrderItemRequest("1", null, -2, null)),
                List.of(new CreateOrderItemRequest("1", null, 1, null), new CreateOrderItemRequest("1", null, 2, null)))) {
            assertThrows(IllegalArgumentException.class, () -> service.createOrder(new CreateOrderRequest("u", items, null), "k"));
        }
        verifyNoInteractions(intents);
    }

    @Test void fingerprintIgnoresDisplayFieldsAndPaymentButIncludesQuantity() {
        var first = new CreateOrderRequest("u", List.of(new CreateOrderItemRequest("1", "name", 1, null)), null);
        var same = new CreateOrderRequest("u", List.of(new CreateOrderItemRequest("1", "changed", 1, null)), new PaymentDetails("pm_ok_abcdef123456"));
        var changed = new CreateOrderRequest("u", List.of(new CreateOrderItemRequest("1", null, 2, null)), null);
        assertEquals(OrderService.hashRequest(first), OrderService.hashRequest(same));
        assertNotEquals(OrderService.hashRequest(first), OrderService.hashRequest(changed));
    }
}
