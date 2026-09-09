package com.gocommerce.analytics.consumer;
import com.gocommerce.analytics.events.OrderCreatedEvent;
import com.gocommerce.analytics.service.OrderEventProjector;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
class OrderEventsConsumerTest {
    @Test void delegatesWholeEventIncludingIdentity() {
        var projector = mock(OrderEventProjector.class);
        var event = new OrderCreatedEvent("o1", "u1", BigDecimal.TEN, "PAID", List.of());
        new OrderEventsConsumer(projector).handleOrderCreated(event);
        verify(projector).recordOrder(event);
    }
}
