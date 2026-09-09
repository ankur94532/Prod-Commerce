package com.gocommerce.orders.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpCatalogClientContractTest {

    @Test
    void parsesTheCatalogProviderContract() throws Exception {
        var client = new HttpCatalogClient("http://catalog", "test-internal", Duration.ofSeconds(1), Duration.ofSeconds(1));
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
        String fixture = Files.readString(Path.of("..", "..", "contracts", "order-catalog", "product-snapshot.json"));
        server.expect(requestTo("http://catalog/api/v1/internal/products/42"))
                .andExpect(header("X-Internal-Service-Token", "test-internal"))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        var product = client.getProductSnapshot("42");

        assertEquals("42", product.productId());
        assertEquals("Acme Commuter Headset", product.productName());
        assertEquals("1299.00", product.unitPrice().toPlainString());
        assertEquals("INR", product.currency());
        server.verify();
    }
}
