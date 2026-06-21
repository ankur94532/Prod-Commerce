package com.gocommerce.search.web;

import com.gocommerce.search.SearchServiceApplication;
import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import com.gocommerce.search.service.ProductEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = SearchServiceApplication.class,
        properties = "search.bootstrap.reindex-empty-on-startup=false"
)
@AutoConfigureMockMvc
class SearchControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private ProductSearchRepository productSearchRepository;

        @MockBean
        private SearchCache searchCache;

        @MockBean
        private ElasticsearchOperations elasticsearchOperations;

        @MockBean
        private ProductEmbeddingService productEmbeddingService;

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

                SearchHit<ProductDocument> hit = new SearchHit<>(
                        "products",
                        doc.getId(),
                        null,
                        1.0f,
                        new Object[0],
                        Map.of(),
                        Map.of(),
                        null,
                        null,
                        List.of(),
                        doc);

                when(elasticsearchOperations.search(
                                any(org.springframework.data.elasticsearch.core.query.Query.class),
                                eq(ProductDocument.class)))
                        .thenReturn(new SearchHitsImpl<>(
                                1,
                                TotalHitsRelation.EQUAL_TO,
                                1.0f,
                                null,
                                null,
                                List.of(hit),
                                null,
                                null,
                                null));

                mockMvc.perform(get("/api/v1/search")
                                .param("q", "laptop"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(1))
                        .andExpect(jsonPath("$.items[0].id").value("p1"))
                        .andExpect(jsonPath("$.items[0].name").value(containsString("MacBook Pro")))
                        .andExpect(jsonPath("$.items[0].category").value("laptops"));
        }

        @Test
        void vectorSearch_returnsResultFromRepository_whenCacheMiss() throws Exception {
                when(searchCache.get(any())).thenReturn(Optional.empty());
                when(productEmbeddingService.embed("comfortable running shoes"))
                        .thenReturn(Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0.01f));

                ProductDocument doc = new ProductDocument(
                        "p2",
                        "road-running-shoes",
                        "Road Running Shoes",
                        "shoes",
                        new BigDecimal("6999"),
                        "INR",
                        List.of("running", "footwear"),
                        "https://example.com/shoes.jpg",
                        0L
                );

                SearchHit<ProductDocument> hit = new SearchHit<>(
                        "products",
                        doc.getId(),
                        null,
                        1.0f,
                        new Object[0],
                        Map.of(),
                        Map.of(),
                        null,
                        null,
                        List.of(),
                        doc);

                when(elasticsearchOperations.search(
                                any(org.springframework.data.elasticsearch.core.query.Query.class),
                                eq(ProductDocument.class)))
                        .thenReturn(new SearchHitsImpl<>(
                                1,
                                TotalHitsRelation.EQUAL_TO,
                                1.0f,
                                null,
                                null,
                                List.of(hit),
                                null,
                                null,
                                null));

                mockMvc.perform(get("/api/v1/search/vector")
                                .param("q", "comfortable running shoes"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(1))
                        .andExpect(jsonPath("$.items[0].id").value("p2"))
                        .andExpect(jsonPath("$.items[0].category").value("shoes"));
        }
}
