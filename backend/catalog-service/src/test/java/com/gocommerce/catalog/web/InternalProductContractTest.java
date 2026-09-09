package com.gocommerce.catalog.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.catalog.dto.ProductResponse;
import com.gocommerce.catalog.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalProductContractTest {

    @Test
    void producesTheProductSnapshotConsumedByOrderService() throws Exception {
        var objectMapper = new ObjectMapper();
        String fixture = Files.readString(Path.of("..", "..", "contracts", "order-catalog", "product-snapshot.json"));
        var service = mock(ProductService.class);
        when(service.getById(42L)).thenReturn(objectMapper.readValue(fixture, ProductResponse.class));
        var mvc = MockMvcBuilders.standaloneSetup(new InternalProductController(service)).build();

        String response = mvc.perform(get("/api/v1/internal/products/42"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response)).isEqualTo(objectMapper.readTree(fixture));
    }
}
