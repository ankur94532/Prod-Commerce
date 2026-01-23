package com.gocommerce.search.web;

import com.gocommerce.search.SearchServiceApplication;
import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SearchServiceApplication.class)
@AutoConfigureMockMvc
class SearchControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private ProductSearchRepository productSearchRepository;

        @MockBean
        private SearchCache searchCache;

        @Test
        void health_returnsOk() throws Exception {
                mockMvc.perform(get("/api/v1/search/health"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("search-service:OK")));
        }

        @Test
        void search_returnsResultFromRepository_whenCacheMiss() throws Exception {
                // Cache miss
                when(searchCache.get(any())).thenReturn(Optional.empty());

                // Fake ES document
                ProductDocument doc = new ProductDocument(
                        "p1",
                        "macbook-pro-14",
                        "MacBook Pro 14 M3",
                        "laptops",
                        new BigDecimal("199900"),
                        "INR",
                        List.of("apple", "laptop"),
                        "https://example.com/mac.jpg",
                        100L      // new popularityScore argument
                );

                Page<ProductDocument> page = new PageImpl<>(
                        List.of(doc),
                        PageRequest.of(0, 20),
                        1);

                when(productSearchRepository
                        .searchByNameOrCategory(
                                anyString(),
                                anyString(),
                                any(Pageable.class)))
                        .thenReturn(page);

                mockMvc.perform(get("/api/v1/search")
                                .param("q", "laptop"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(1))
                        .andExpect(jsonPath("$.items[0].id").value("p1"))
                        .andExpect(jsonPath("$.items[0].name").value(containsString("MacBook Pro")))
                        .andExpect(jsonPath("$.items[0].category").value("laptops"));
        }
}
