package com.gocommerce.search.web;

import com.gocommerce.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchControllerReindexTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Simple stub SearchService – no Mockito involved
        SearchService stubService = new SearchService(null, null, null, null) {
            @Override
            public int reindexProducts() {
                return 42;
            }
        };

        SearchController controller = new SearchController(stubService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void reindex_returnsIndexedCount() throws Exception {
        mockMvc.perform(post("/api/v1/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(42));
    }
}
