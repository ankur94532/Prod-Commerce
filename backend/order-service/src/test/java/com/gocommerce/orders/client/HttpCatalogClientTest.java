package com.gocommerce.orders.client;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpCatalogClientTest {
    @Test void retryAndCompensationCarryTheSameReservationIdentity() {
        var client = new HttpCatalogClient("http://catalog", "test-internal", Duration.ofSeconds(1), Duration.ofSeconds(1));
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
        server.expect(requestTo("http://catalog/api/v1/internal/inventory/products/1/decrement?quantity=2"))
                .andExpect(header("Idempotency-Key", "order:1:line:2"))
                .andExpect(header("X-Internal-Service-Token", "test-internal")).andRespond(withServerError());
        server.expect(requestTo("http://catalog/api/v1/internal/inventory/products/1/decrement?quantity=2"))
                .andExpect(header("Idempotency-Key", "order:1:line:2")).andRespond(withNoContent());
        server.expect(requestTo("http://catalog/api/v1/internal/inventory/products/1/increment?quantity=2"))
                .andExpect(header("Idempotency-Key", "order:1:line:2")).andRespond(withNoContent());
        assertThrows(RuntimeException.class, () -> client.decrementStock("1", 2, "order:1:line:2"));
        client.decrementStock("1", 2, "order:1:line:2");
        client.incrementStock("1", 2, "order:1:line:2");
        server.verify();
    }
}
