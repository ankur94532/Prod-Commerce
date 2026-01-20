package com.gocommerce.search.web;

import com.gocommerce.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SearchController.class)
@AutoConfigureMockMvc(addFilters = false) // disable default security filters
class SearchControllerReindexTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void reindex_returnsIndexedCount() throws Exception {
        when(searchService.reindexProducts()).thenReturn(42);

        mockMvc.perform(post("/api/v1/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(42));
    }
}
