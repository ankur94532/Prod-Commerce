package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import com.gocommerce.search.dto.SearchDtos.SearchResultItem;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    @Test
    void search_returnsCachedResult_whenCacheHit() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);

        // catalogClient + elasticsearchOperations not used in search(), so pass null
        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations);

        SearchRequest req = new SearchRequest("mac", null, 0, 20);
        SearchResponse cachedResponse = new SearchResponse(
                List.of(new SearchResultItem(
                        "p1",
                        "macbook-pro-14",
                        "MacBook Pro 14",
                        "laptops",
                        new BigDecimal("199900"),
                        "INR",
                        "https://example.com/mac.jpg")),
                1);

        when(cache.get(req)).thenReturn(Optional.of(cachedResponse));

        // Act
        SearchResponse result = service.search(req);

        // Assert
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("p1");

        verifyNoInteractions(repo);
        verifyNoInteractions(elasticsearchOperations);
        verify(cache, never()).put(any(), any());
    }

    @Test
    void search_hitsRepositoryAndCachesResult_whenCacheMiss() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations);

        SearchRequest req = new SearchRequest("laptop", null, 0, 20);

        when(cache.get(req)).thenReturn(Optional.empty());

        ProductDocument doc = new ProductDocument(
                "p2",
                "gaming-laptop",
                "Gaming Laptop",
                "laptops",
                new BigDecimal("99999"),
                "INR",
                List.of("gaming", "laptop"),
                "https://example.com/gaming.jpg",
                42L          // new popularityScore argument
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        // Act
        SearchResponse result = service.search(req);

        // Assert
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("p2");
        assertThat(result.items().get(0).name()).contains("Gaming Laptop");

        verifyNoInteractions(repo);
        verify(elasticsearchOperations, times(1)).search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class));
        verify(cache, times(1)).put(eq(req), any(SearchResponse.class));
    }

    private SearchHits<ProductDocument> searchHits(ProductDocument doc, long total) {
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

        return new SearchHitsImpl<>(
                total,
                TotalHitsRelation.EQUAL_TO,
                1.0f,
                null,
                null,
                List.of(hit),
                null,
                null,
                null);
    }
}
