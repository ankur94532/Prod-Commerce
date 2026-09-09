package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.config.ProductIndexSettings;
import com.gocommerce.search.config.SearchProperties;
import com.gocommerce.search.dto.SearchDtos.*;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(SearchRetrievalElasticsearchTest.Config.class)
@EnabledIfEnvironmentVariable(named = "SEARCH_TEST_ES_ADDRESS", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchRetrievalElasticsearchTest {
    @Configuration
    static class Config extends ElasticsearchConfiguration {
        @Override public ClientConfiguration clientConfiguration() {
            return ClientConfiguration.builder().connectedTo(System.getenv("SEARCH_TEST_ES_ADDRESS")).build();
        }
    }
    @Autowired ElasticsearchOperations operations;
    final IndexCoordinates index = IndexCoordinates.of("retrieval-test-" + UUID.randomUUID());
    SearchService service;
    SearchCache cache;

    static List<Float> vector(float x, float y) {
        var values = new ArrayList<>(Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0f));
        values.set(0, x); values.set(1, y); return values;
    }
    ProductDocument product(int id, String name, String category, int price, int stock, String brand, String material, List<Float> vector) {
        var doc = new ProductDocument(String.valueOf(id), "fixture-" + id, name, "Deterministic contract fixture", brand, category, (long) id,
                BigDecimal.valueOf(price), "INR", List.of(), null, stock, "black", "over-ear", "regular", "128GB", "8GB", material,
                "audio " + name, 0L);
        doc.setSearchEmbedding(vector);
        return doc;
    }
    @BeforeAll void createFixtureIndex() {
        var indexOps = operations.indexOps(index);
        indexOps.create(ProductIndexSettings.settings());
        indexOps.putMapping(operations.indexOps(ProductDocument.class).createMapping());
        operations.save(List.of(
                product(1, "Hush commuter headset", "earbuds-headphones", 40, 5, "Acme", "mesh", vector(1, 0)),
                product(2, "Passive ear protectors", "earbuds-headphones", 20, 5, "Acme", "mesh", vector(.9f, .1f)),
                product(3, "Expensive headset", "earbuds-headphones", 90, 5, "Acme", "mesh", vector(.8f, .2f)),
                product(4, "Unavailable headset", "earbuds-headphones", 10, 0, "Acme", "mesh", vector(.7f, .3f)),
                product(5, "Other brand headset", "earbuds-headphones", 30, 5, "Else", "mesh", vector(.6f, .4f)),
                product(6, "Different material headset", "earbuds-headphones", 35, 5, "Acme", "plastic", vector(.5f, .5f)),
                product(7, "Quiet journeys journal", "books-stationery", 15, 5, "Acme", "paper", vector(0, 1)),
                product(8, "Quiet journeys diary", "books-stationery", 15, 5, "Acme", "paper", null)
        ), index);
        indexOps.refresh();
    }
    @AfterAll void removeFixtureIndex() { operations.indexOps(index).delete(); }

    @Test void denseVectorMappingEnablesCosineHnswIndexing() {
        var mapping = operations.indexOps(index).getMapping();
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) mapping.get("properties");
        @SuppressWarnings("unchecked")
        var embedding = (Map<String, Object>) properties.get("searchEmbedding");
        assertThat(embedding)
                .containsEntry("type", "dense_vector")
                .containsEntry("dims", ProductDocument.SEARCH_EMBEDDING_DIMENSIONS)
                .containsEntry("index", true)
                .containsEntry("similarity", "cosine");
    }
    @BeforeEach void setupService() {
        // Route production-built queries to a unique test index; never use/delete the products index.
        var routed = mock(ElasticsearchOperations.class);
        when(routed.search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class)))
                .thenAnswer(i -> operations.search(
                        (org.springframework.data.elasticsearch.core.query.Query) i.getArgument(0), ProductDocument.class, index));
        cache = mock(SearchCache.class);
        when(cache.get(any())).thenReturn(Optional.empty());
        var embedding = mock(ProductEmbeddingService.class);
        when(embedding.embed(anyString())).thenReturn(vector(1, 0));
        var properties = new SearchProperties();
        properties.getRrf().setCandidateWindow(2);
        service = new SearchService(mock(ProductSearchRepository.class), cache, null, routed, null, embedding, properties);
    }
    SearchRequest request(String mode, String sort, int page, int size) {
        return new SearchRequest("audio", "earbuds-headphones", "acme", BigDecimal.valueOf(15), BigDecimal.valueOf(50), true,
                "black", "over-ear", "regular", "128GB", "8GB", "mesh", sort, mode, page, size);
    }

    @ParameterizedTest @ValueSource(strings = {"text", "vector", "vector_exact", "hybrid", "hybrid_rrf"})
    void explicitFiltersAreIdenticalAcrossRetrievalModes(String mode) {
        var result = service.search(request(mode, "relevance", 0, 20));
        assertThat(result.items()).extracting(SearchResultItem::id).containsExactlyInAnyOrder("1", "2");
        assertThat(result.retrieval().mode()).isEqualTo(mode);
    }

    @ParameterizedTest @ValueSource(strings = {"text", "vector", "vector_exact", "hybrid"})
    void priceSortIsHonoredEvenWhenVectorScoringDisagrees(String mode) {
        var ascending = service.search(request(mode, "price_asc", 0, 20));
        var descending = service.search(request(mode, "price_desc", 0, 20));
        assertThat(ascending.items()).extracting(SearchResultItem::id).containsExactly("2", "1");
        assertThat(descending.items()).extracting(SearchResultItem::id).containsExactly("1", "2");
        var captured = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(cache, times(2)).put(captured.capture(), any());
        assertThat(captured.getAllValues()).extracting(SearchRequest::sort).containsExactly("price_asc", "price_desc");
    }

    @Test void rrfIncludesVectorOnlyAndLexicalOnlyCandidatesWithBoundedTotals() {
        var legacy = service.search(new SearchRequest("quiet journeys", null, "hybrid", 0, 20));
        var union = service.search(new SearchRequest("quiet journeys", null, "hybrid_rrf", 0, 20));
        assertThat(legacy.items()).extracting(SearchResultItem::id).containsExactly("7");
        assertThat(union.items()).extracting(SearchResultItem::id).containsExactlyInAnyOrder("1", "2", "7", "8");
        assertThat(union.retrieval().totalRelation()).isEqualTo("candidate_union");
        assertThat(union.retrieval().candidateWindow()).isEqualTo(2);
        assertThat(union.total()).isEqualTo(4);
    }

    @Test void rrfFusesDuplicateCandidateOnceAndPaginatesOneStableUnion() {
        var all = service.search(request("hybrid_rrf", "relevance", 0, 20));
        var page0 = service.search(request("hybrid_rrf", "relevance", 0, 1));
        var page1 = service.search(request("hybrid_rrf", "relevance", 1, 1));
        assertThat(all.total()).isEqualTo(2);
        assertThat(List.of(page0.items().get(0).id(), page1.items().get(0).id()))
                .containsExactlyElementsOf(all.items().stream().map(SearchResultItem::id).toList());
    }

    @Test void genuineZeroHitsRemainSuccessfulWithRetrievalMetadata() {
        var result = service.search(new SearchRequest("audio", "absent-category", "vector", 0, 20));
        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.retrieval().algorithm()).isEqualTo("hnsw_cosine");
        assertThat(result.retrieval().totalRelation()).isEqualTo("ann_candidates");
    }

    @Test void annAndExactVectorModesCanBeComparedOnTheSameFilters() {
        var ann = service.search(request("vector", "relevance", 0, 20));
        var exact = service.search(request("vector_exact", "relevance", 0, 20));

        assertThat(ann.items()).extracting(SearchResultItem::id).containsExactly("1", "2");
        assertThat(exact.items()).extracting(SearchResultItem::id).containsExactly("1", "2");
        assertThat(ann.retrieval().algorithm()).isEqualTo("hnsw_cosine");
        assertThat(exact.retrieval().algorithm()).isEqualTo("exact_cosine");
    }
}
