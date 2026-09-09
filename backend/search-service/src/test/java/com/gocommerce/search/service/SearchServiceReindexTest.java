package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.client.CatalogClient;
import com.gocommerce.search.client.CatalogClient.CatalogProductPage;
import com.gocommerce.search.config.CatalogProperties;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceReindexTest {

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private SearchCache searchCache;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    private TestCatalogClient catalogClient;
    private SearchService searchService;

    // Simple stub – no HTTP calls
    static class TestCatalogClient extends CatalogClient {

        private List<Map<String, Object>> products = List.of();

        TestCatalogClient() {
            super(null, new CatalogProperties());
        }

        void setProducts(List<Map<String, Object>> products) {
            this.products = products;
        }

        @Override
        public List<Map<String, Object>> fetchAllProducts() {
            return products;
        }

        @Override
        public CatalogProductPage fetchProductsPage(int page, int size) {
            if (page > 0) {
                return new CatalogProductPage(List.of(), page, size, 1, products.size());
            }
            return new CatalogProductPage(products, page, size, 1, products.size());
        }
    }

    @BeforeEach
    void setUp() {
        // Rebuilds now target a freshly created index and only move the alias at the end,
        // so every index operation is addressed by name rather than by document class.
        lenient().when(elasticsearchOperations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        lenient().when(elasticsearchOperations.indexOps(ProductDocument.class)).thenReturn(indexOperations);
        lenient().when(indexOperations.getAliases(anyString())).thenReturn(Map.of());

        catalogClient = new TestCatalogClient();

        searchService = new SearchService(
                productSearchRepository,
                searchCache,
                catalogClient,
                elasticsearchOperations
        );
    }

    @Test
    void reindexBuildsANewIndexAndPromotesItWithoutDeletingTheLiveOneFirst() {
        when(elasticsearchOperations.count(any(), eq(ProductDocument.class), any(IndexCoordinates.class)))
                .thenReturn(1L);

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", "1");
        p1.put("name", "Galaxy S26 Ultra");
        p1.put("category", "smartphones");
        p1.put("price", new BigDecimal("89999"));
        p1.put("currency", "INR");
        p1.put("thumbnailUrl", "https://example.com/s26.jpg");

        catalogClient.setProducts(List.of(p1));

        int count = searchService.reindexProducts();

        assertEquals(1, count);
        // The old layout deleted the live index before rebuilding; nothing is deleted now
        // until the alias has already moved.
        verify(indexOperations).create(anyMap());
        verify(indexOperations).alias(any());
        verify(elasticsearchOperations).save(anyList(), any(IndexCoordinates.class));
        verify(indexOperations).refresh();
        verify(searchCache).clear();
    }

    @Test
    void documentsAreWrittenToTheBuildIndexNotThroughTheLiveAlias() {
        when(elasticsearchOperations.count(any(), eq(ProductDocument.class), any(IndexCoordinates.class)))
                .thenReturn(1L);
        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", "1");
        p1.put("name", "Galaxy S26 Ultra");
        catalogClient.setProducts(List.of(p1));

        searchService.reindexProducts();

        var coordinates = org.mockito.ArgumentCaptor.forClass(IndexCoordinates.class);
        verify(elasticsearchOperations).save(anyList(), coordinates.capture());
        assertNotEquals(SearchIndexManager.ALIAS, coordinates.getValue().getIndexName());
        assertTrue(coordinates.getValue().getIndexName().startsWith(SearchIndexManager.ALIAS + "-"));
    }

    @Test
    void anInconsistentRebuildIsNeitherPromotedNorLeftBehind() {
        // Elasticsearch ends up with fewer documents than the catalog handed over.
        when(elasticsearchOperations.count(any(), eq(ProductDocument.class), any(IndexCoordinates.class)))
                .thenReturn(0L);

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", "1");
        p1.put("name", "Galaxy S26 Ultra");
        catalogClient.setProducts(List.of(p1));

        ReindexResult result = searchService.reindexProductsDetailed();

        assertEquals(1, result.indexed());
        assertEquals(1, result.catalogProducts());
        assertEquals(0, result.indexedDocuments());
        assertEquals(false, result.consistent());
        verify(indexOperations, never()).alias(any());
        verify(indexOperations).delete();
        verify(searchCache, never()).clear();
    }

    @Test
    void reindexProducts_skipsProductsWithoutId() {
        when(elasticsearchOperations.count(any(), eq(ProductDocument.class), any(IndexCoordinates.class)))
                .thenReturn(0L);

        Map<String, Object> p1 = new HashMap<>();
        p1.put("name", "No Id Product");
        p1.put("price", new BigDecimal("1000"));

        catalogClient.setProducts(List.of(p1));

        int count = searchService.reindexProducts();

        assertEquals(0, count);
        verify(elasticsearchOperations, never()).save(anyList(), any(IndexCoordinates.class));
    }
}
