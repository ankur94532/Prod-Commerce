package com.gocommerce.catalog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = com.gocommerce.catalog.CatalogServiceApplication.class,
        properties = {
                "security.jwt.secret=test_catalog_secret_very_long_1234567890"
        }
)
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listProducts_returnsOkAndDataArray() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getProductBySlug_returnsExpectedProduct_whenExists() throws Exception {
        // relies on seed data in CatalogServiceApplication
        mockMvc.perform(get("/api/v1/products/s26-ultra-256gb-gray"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("s26-ultra-256gb-gray"))
                .andExpect(jsonPath("$.data.name").value("Galaxy S26 Ultra 256GB (Gray)"));
    }
}
