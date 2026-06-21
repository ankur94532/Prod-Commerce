package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import com.gocommerce.search.dto.SearchDtos.SearchResultItem;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;

import java.math.BigDecimal;
import java.util.Collections;
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
        SearchRequest normalizedReq = new SearchRequest("mac", null, "hybrid", 0, 20);
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

        when(cache.get(normalizedReq)).thenReturn(Optional.of(cachedResponse));

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
    void search_usesHybridQueryByDefault_whenCacheMiss() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("laptop", null, 0, 20);

        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("laptop")).thenReturn(testVector());

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
        verify(embeddingService).embed("laptop");
        verify(elasticsearchOperations, times(1)).search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class));
        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        assertThat(nativeQuery.getQuery().isScriptScore()).isTrue();

        ArgumentCaptor<SearchRequest> cacheRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(cache, times(1)).put(cacheRequestCaptor.capture(), any(SearchResponse.class));
        assertThat(cacheRequestCaptor.getValue().mode()).isEqualTo("hybrid");
    }

    @Test
    void search_usesKeywordQuery_whenModeIsText() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("laptop", null, "text", 0, 20);
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
                42L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        SearchResponse result = service.search(req);

        assertThat(result.total()).isEqualTo(1);
        verifyNoInteractions(embeddingService);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        assertThat(nativeQuery.getQuery().isBool()).isTrue();
        verify(cache).put(eq(req), any(SearchResponse.class));
    }

    @Test
    void search_expandsMobileQueryForPhoneAndSmartphoneMatches() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("mobile", null, 0, 20);
        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("mobile")).thenReturn(testVector());

        ProductDocument doc = new ProductDocument(
                "p4",
                "iphone-17",
                "iPhone 17",
                "smartphones",
                new BigDecimal("79999"),
                "INR",
                List.of("phone", "smartphone"),
                "https://example.com/iphone.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        SearchResponse result = service.search(req);

        assertThat(result.total()).isEqualTo(1);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        String queryJson = nativeQuery.getQuery().toString();
        assertThat(queryJson)
                .contains("phone")
                .contains("smartphone")
                .contains("smartphones");
    }

    @Test
    void search_infersAccessoryCategoryForPhoneStandQuery() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("phone stand", null, 0, 20);
        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("phone stand")).thenReturn(testVector());

        ProductDocument doc = new ProductDocument(
                "p5",
                "phone-stand",
                "Aluminium Phone Stand",
                "accessories-cables",
                new BigDecimal("499"),
                "INR",
                List.of("phone", "stand"),
                "https://example.com/stand.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        SearchResponse result = service.search(req);

        assertThat(result.total()).isEqualTo(1);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        String queryJson = nativeQuery.getQuery().toString();
        assertThat(queryJson)
                .contains("accessories-cables")
                .doesNotContain("smartphones");
    }

    @Test
    void search_boostsKnownBrandAtStartOfQuery() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("AND studio", null, 0, 20);
        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("AND studio")).thenReturn(testVector());

        ProductDocument doc = new ProductDocument(
                "p6",
                "women-wrap-dress-rust",
                "Women Wrap Dress Studio",
                "womens-dresses",
                new BigDecimal("1799"),
                "INR",
                List.of("dress"),
                "https://example.com/dress.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        service.search(req);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        String queryJson = nativeQuery.getQuery().toString();
        assertThat(queryJson)
                .contains("brand")
                .contains("AND")
                .contains("18.0");
    }

    @Test
    void search_doesNotBoostAndBrandInMiddleOfNaturalQuery() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("shirts and pants", null, 0, 20);
        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("shirts and pants")).thenReturn(testVector());

        ProductDocument doc = new ProductDocument(
                "p7",
                "cargo-pants",
                "Men Cargo Pants",
                "mens-jeans-trousers",
                new BigDecimal("1899"),
                "INR",
                List.of("pants"),
                "https://example.com/pants.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        service.search(req);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        String queryJson = nativeQuery.getQuery().toString();
        assertThat(queryJson).doesNotContain("value=AND");
    }

    @Test
    void search_usesVectorQuery_whenModeIsVector() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("comfortable running shoes", null, "vector", 0, 20);
        when(cache.get(req)).thenReturn(Optional.empty());
        when(embeddingService.embed("comfortable running shoes")).thenReturn(testVector());

        ProductDocument doc = new ProductDocument(
                "p3",
                "running-shoes",
                "Road Running Shoes",
                "shoes",
                new BigDecimal("6999"),
                "INR",
                List.of("running", "footwear"),
                "https://example.com/shoes.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        SearchResponse result = service.search(req);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).id()).isEqualTo("p3");

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        assertThat(nativeQuery.getQuery().isScriptScore()).isTrue();
        verify(cache).put(eq(req), any(SearchResponse.class));
    }

    @Test
    void search_returnsEmptyVectorResult_whenQueryEmbedsToZeroVector() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("!!!", null, "vector", 0, 20);
        when(cache.get(req)).thenReturn(Optional.empty());
        when(embeddingService.embed("!!!")).thenReturn(Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0.0f));

        SearchResponse result = service.search(req);

        assertThat(result.total()).isZero();
        assertThat(result.items()).isEmpty();
        verifyNoInteractions(elasticsearchOperations);
        verify(cache).put(eq(req), any(SearchResponse.class));
    }

    @Test
    void search_returnsEmptyResultForBlankQuery_withoutTouchingCacheOrElasticsearch() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations);

        SearchResponse result = service.search(new SearchRequest("   ", null, 0, 20));

        assertThat(result.total()).isZero();
        assertThat(result.items()).isEmpty();
        verifyNoInteractions(cache);
        verifyNoInteractions(repo);
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    void search_normalizesPageAndCapsResultSize() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("laptop", null, -5, 500);
        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("laptop")).thenReturn(testVector());

        ProductDocument doc = new ProductDocument(
                "p8",
                "office-laptop",
                "Office Laptop",
                "laptops",
                new BigDecimal("59999"),
                "INR",
                List.of("laptop"),
                "https://example.com/laptop.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        SearchResponse result = service.search(req);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);

        ArgumentCaptor<SearchRequest> cacheRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(cache).put(cacheRequestCaptor.capture(), any(SearchResponse.class));
        assertThat(cacheRequestCaptor.getValue().page()).isZero();
        assertThat(cacheRequestCaptor.getValue().size()).isEqualTo(100);
    }

    @Test
    void hybridSearchFallsBackToKeywordQuery_whenEmbeddingIsZeroVector() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest("unknown punctuation", null, 0, 20);
        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("unknown punctuation"))
                .thenReturn(Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0.0f));

        ProductDocument doc = new ProductDocument(
                "p9",
                "fallback-product",
                "Fallback Product",
                "accessories-cables",
                new BigDecimal("999"),
                "INR",
                List.of("fallback"),
                "https://example.com/fallback.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        SearchResponse result = service.search(req);

        assertThat(result.total()).isEqualTo(1);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        assertThat(nativeQuery.getQuery().isBool()).isTrue();
    }

    @Test
    void search_appliesFiltersForBrandPriceStockAndAttributes() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);

        SearchRequest req = new SearchRequest(
                "running shoes",
                "footwear",
                "nike",
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                true,
                "black",
                "running",
                "regular",
                null,
                null,
                "mesh",
                "relevance",
                0,
                20
        );
        when(cache.get(any())).thenReturn(Optional.empty());
        when(embeddingService.embed("running shoes")).thenReturn(testVector());

        ProductDocument doc = new ProductDocument(
                "p10",
                "nike-running-shoe",
                "Nike Running Shoe",
                "footwear",
                new BigDecimal("3999"),
                "INR",
                List.of("running", "shoes"),
                "https://example.com/nike.jpg",
                0L
        );

        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class)))
                .thenReturn(searchHits(doc, 1));

        service.search(req);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> queryCaptor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ProductDocument.class));
        NativeQuery nativeQuery = (NativeQuery) queryCaptor.getValue();
        String queryJson = nativeQuery.getQuery().toString();
        assertThat(queryJson)
                .contains("category")
                .contains("footwear")
                .contains("brand")
                .contains("nike")
                .contains("price")
                .contains("stockQuantity")
                .contains("color")
                .contains("black")
                .contains("type")
                .contains("running")
                .contains("fit")
                .contains("regular")
                .contains("material")
                .contains("mesh");
    }

    @Test
    void indexProductFromPayload_generatesSearchEmbedding() {
        ProductSearchRepository repo = mock(ProductSearchRepository.class);
        SearchCache cache = mock(SearchCache.class);
        ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        ProductEmbeddingService embeddingService = mock(ProductEmbeddingService.class);

        when(elasticsearchOperations.indexOps(ProductDocument.class)).thenReturn(indexOperations);
        when(embeddingService.embed(anyString())).thenReturn(testVector());

        SearchService service = new SearchService(repo, cache, null, elasticsearchOperations, null, embeddingService);
        service.indexProductFromPayload(Map.of(
                "id", "4",
                "name", "Cotton Hoodie",
                "description", "Heavyweight cotton fleece",
                "category", "apparel",
                "tags", List.of("hoodie", "winter")
        ));

        ArgumentCaptor<ProductDocument> documentCaptor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(repo).save(documentCaptor.capture());
        ProductDocument saved = documentCaptor.getValue();
        assertThat(saved.getSearchText()).contains("Cotton Hoodie", "Heavyweight cotton fleece", "hoodie");
        assertThat(saved.getSearchEmbedding()).hasSize(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS);
        verify(indexOperations).refresh();
        verify(cache).clear();
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

    private List<Float> testVector() {
        return Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0.01f);
    }
}
