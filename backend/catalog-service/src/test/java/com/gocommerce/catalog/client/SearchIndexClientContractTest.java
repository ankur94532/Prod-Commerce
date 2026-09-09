package com.gocommerce.catalog.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.catalog.entity.Product;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIndexClientContractTest {

    @Test
    void sendsTheIndexProductContractAcceptedBySearchService() throws Exception {
        var received = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/search/index-product", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"));
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var attributes = new LinkedHashMap<String, String>();
            attributes.put("color", "black");
            attributes.put("material", "mesh");
            var product = new Product(
                    "acme-commuter-headset", "Acme Commuter Headset", "Synthetic contract fixture",
                    new BigDecimal("1299.00"), "INR", "earbuds-headphones", "Acme",
                    List.of("https://example.invalid/products/42.png"), 7, true,
                    attributes);
            ReflectionTestUtils.setField(product, "id", 42L);
            var client = new SearchIndexClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1), Duration.ofSeconds(1), "test-internal");

            client.indexProduct(product);

            var mapper = new ObjectMapper();
            String fixture = Files.readString(Path.of("..", "..", "contracts", "catalog-search", "index-product.json"));
            assertThat(mapper.readTree(received.get())).isEqualTo(mapper.readTree(fixture));
            assertThat(token.get()).isEqualTo("test-internal");
        } finally {
            server.stop(0);
        }
    }
}
