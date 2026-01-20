package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.client.CatalogClient;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceReindexTest {

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private SearchCache searchCache;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        when(elasticsearchOperations.indexOps(ProductDocument.class))
                .thenReturn(indexOperations);

        searchService = new SearchService(
                productSearchRepository,
                searchCache,
                catalogClient,
                elasticsearchOperations
        );
    }

    @Test
    void reindexProducts_recreatesIndexAndReturnsDocumentCount() {
        when(indexOperations.exists()).thenReturn(true);

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", "1");
        p1.put("name", "Galaxy S26 Ultra");
        p1.put("category", "smartphones");
        p1.put("price", new BigDecimal("89999"));
        p1.put("currency", "INR");
        p1.put("thumbnailUrl", "https://example.com/s26.jpg");

        when(catalogClient.fetchAllProducts()).thenReturn(List.of(p1));

        int count = searchService.reindexProducts();

        assertEquals(1, count);

        verify(indexOperations).delete();
        verify(indexOperations).create();
        verify(productSearchRepository).saveAll(anyList());
        verify(indexOperations).refresh();
    }

    @Test
    void reindexProducts_skipsProductsWithoutId() {
        when(indexOperations.exists()).thenReturn(false);

        Map<String, Object> p1 = new HashMap<>();
        p1.put("name", "No Id Product");
        p1.put("price", new BigDecimal("1000"));

        when(catalogClient.fetchAllProducts()).thenReturn(List.of(p1));

        int count = searchService.reindexProducts();

        // no valid docs -> 0 indexed
        assertEquals(0, count);
        verify(productSearchRepository).saveAll(anyList());
    }
}
