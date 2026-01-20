package com.gocommerce.catalog.web;

import com.gocommerce.catalog.entity.Product;
import com.gocommerce.catalog.service.AdminProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MVC test for AdminProductController.
 * No Spring Boot context, no Mockito.
 */
class AdminProductControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminProductService adminProductService = new AdminProductService(null) {
            @Override
            public Product updateStatus(Long id, boolean active) {
                Product p = new Product();
                p.setActive(active);

                // ✅ Give it a non-null id so Map.of(...) doesn't NPE
                try {
                    Field idField = Product.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(p, id);  // use the passed id
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return p;
            }
        };

        AdminProductController controller = new AdminProductController(adminProductService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void updateStatus_parsesBodyAndReturnsActiveFlag() throws Exception {
        Long productId = 42L;

        mockMvc.perform(
                        patch("/api/v1/admin/products/{id}/status", productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\": true}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.intValue()))
                .andExpect(jsonPath("$.active").value(true));
    }
}
