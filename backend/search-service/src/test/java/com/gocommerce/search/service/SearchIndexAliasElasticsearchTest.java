package com.gocommerce.search.service;

import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.client.CatalogClient;
import com.gocommerce.search.config.ProductIndexSettings;
import com.gocommerce.search.config.SearchProperties;
import com.gocommerce.search.model.ProductDocument;
import com.gocommerce.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rebuilding used to delete the live index first, so a failure left search empty. These
 * tests drive real Elasticsearch and assert the property that matters: readers never see a
 * partial rebuild, and a failed rebuild changes nothing.
 */
@SpringJUnitConfig(SearchIndexAliasElasticsearchTest.Config.class)
@EnabledIfEnvironmentVariable(named = "SEARCH_TEST_ES_ADDRESS", matches = ".+")
class SearchIndexAliasElasticsearchTest {

    @Configuration
    static class Config extends ElasticsearchConfiguration {
        @Override
        public ClientConfiguration clientConfiguration() {
            return ClientConfiguration.builder().connectedTo(System.getenv("SEARCH_TEST_ES_ADDRESS")).build();
        }
    }

    @Autowired
    ElasticsearchOperations operations;

    SearchIndexManager manager;
    CatalogClient catalog;
    SearchService service;

    private void deleteEverything() {
        for (var information : operations.indexOps(IndexCoordinates.of("products*")).getInformation()) {
            try {
                operations.indexOps(IndexCoordinates.of(information.getName())).delete();
            } catch (RuntimeException ignored) {
                // Already gone.
            }
        }
    }

    @BeforeEach
    void setUp() {
        deleteEverything();
        manager = new SearchIndexManager(operations);
        catalog = mock(CatalogClient.class);
        SearchCache cache = mock(SearchCache.class);
        when(cache.get(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        // Deterministic embeddings: this suite is about index lifecycle, not relevance.
        ProductEmbeddingService embeddings = mock(ProductEmbeddingService.class);
        when(embeddings.dimensions()).thenReturn(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS);
        when(embeddings.embed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0.01f));
        when(embeddings.embedAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream()
                    .map(text -> java.util.Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0.01f))
                    .toList();
        });
        service = new SearchService(mock(ProductSearchRepository.class), cache, catalog, operations, null,
                embeddings, new SearchProperties());
    }

    @AfterEach
    void tearDown() {
        deleteEverything();
    }

    private Map<String, Object> product(int id, String name) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("slug", "slug-" + id);
        row.put("name", name);
        row.put("description", "fixture");
        row.put("brand", "acme");
        row.put("categorySlug", "earbuds-headphones");
        row.put("price", 10);
        row.put("currency", "INR");
        row.put("stockQuantity", 5);
        return row;
    }

    private void catalogReturns(int count, String namePrefix) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(product(i, namePrefix + " " + i));
        }
        when(catalog.fetchProductsPage(anyInt(), anyInt()))
                .thenReturn(new CatalogClient.CatalogProductPage(rows, 0, Math.max(count, 1), 1, count));
    }

    private long documentsBehindAlias() {
        operations.indexOps(IndexCoordinates.of(SearchIndexManager.ALIAS)).refresh();
        return operations.count(Query.findAll(), ProductDocument.class,
                IndexCoordinates.of(SearchIndexManager.ALIAS));
    }

    @Test
    void aFirstRebuildCreatesAnAliasRatherThanAConcreteIndex() {
        catalogReturns(3, "first");

        ReindexResult result = service.reindexProductsDetailed();

        assertThat(result.consistent()).isTrue();
        assertThat(result.indexed()).isEqualTo(3);
        assertThat(manager.currentIndex())
                .as("the alias must resolve to a timestamped concrete index")
                .isNotNull()
                .startsWith(SearchIndexManager.ALIAS + "-")
                .isNotEqualTo(SearchIndexManager.ALIAS);
        assertThat(documentsBehindAlias()).isEqualTo(3);
    }

    @Test
    void asecondRebuildSwapsTheAliasAndRetiresTheOldIndex() {
        catalogReturns(3, "first");
        service.reindexProductsDetailed();
        String firstIndex = manager.currentIndex();

        catalogReturns(5, "second");
        ReindexResult result = service.reindexProductsDetailed();

        String secondIndex = manager.currentIndex();
        assertThat(result.consistent()).isTrue();
        assertThat(secondIndex).isNotEqualTo(firstIndex);
        assertThat(documentsBehindAlias()).isEqualTo(5);
        assertThat(operations.indexOps(IndexCoordinates.of(firstIndex)).exists())
                .as("the retired index is deleted only after the alias moved")
                .isFalse();
    }

    @Test
    void aFailedRebuildLeavesTheLiveIndexServingTheOldData() {
        catalogReturns(3, "first");
        service.reindexProductsDetailed();
        String liveIndex = manager.currentIndex();

        when(catalog.fetchProductsPage(anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("catalog unavailable"));
        ReindexResult result = service.reindexProductsDetailed();

        assertThat(result.consistent()).isFalse();
        assertThat(manager.currentIndex())
                .as("a failed rebuild must not move the alias")
                .isEqualTo(liveIndex);
        assertThat(documentsBehindAlias())
                .as("readers keep seeing the previous complete index")
                .isEqualTo(3);
    }

    @Test
    void aFailedRebuildDoesNotLeaveItsHalfBuiltIndexBehindAsGarbage() {
        catalogReturns(2, "first");
        service.reindexProductsDetailed();
        String liveIndex = manager.currentIndex();

        when(catalog.fetchProductsPage(anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("catalog unavailable"));
        service.reindexProductsDetailed();

        List<String> remaining = new ArrayList<>();
        for (var information : operations.indexOps(IndexCoordinates.of("products-*")).getInformation()) {
            remaining.add(information.getName());
        }
        assertThat(remaining).containsExactly(liveIndex);
    }

    @Test
    void anInconsistentRebuildIsRefusedRatherThanPublished() {
        catalogReturns(4, "first");
        service.reindexProductsDetailed();
        String liveIndex = manager.currentIndex();

        // The catalog claims more products than it actually hands over: publishing this
        // would silently replace a complete index with an incomplete one.
        List<Map<String, Object>> partial = List.of(product(1, "only one"));
        when(catalog.fetchProductsPage(anyInt(), anyInt()))
                .thenReturn(new CatalogClient.CatalogProductPage(partial, 0, 100, 1, 99L));

        ReindexResult result = service.reindexProductsDetailed();

        assertThat(result.consistent()).isFalse();
        assertThat(manager.currentIndex()).isEqualTo(liveIndex);
        assertThat(documentsBehindAlias()).isEqualTo(4);
    }

    @Test
    void aLegacyConcreteIndexIsMigratedToTheAliasLayout() {
        // The layout before this change: products as a real index holding the documents.
        var legacy = operations.indexOps(IndexCoordinates.of(SearchIndexManager.ALIAS));
        legacy.create(ProductIndexSettings.settings());
        legacy.putMapping(operations.indexOps(ProductDocument.class).createMapping());
        assertThat(manager.hasLegacyConcreteIndex()).isTrue();
        assertThat(manager.currentIndex()).isNull();

        catalogReturns(3, "migrated");
        ReindexResult result = service.reindexProductsDetailed();

        assertThat(result.consistent()).isTrue();
        assertThat(manager.hasLegacyConcreteIndex()).isFalse();
        assertThat(manager.currentIndex()).startsWith(SearchIndexManager.ALIAS + "-");
        assertThat(documentsBehindAlias()).isEqualTo(3);
    }

    @Test
    void theAliasAcceptsWritesSoSingleDocumentUpdatesStillWork() {
        // An alias over more than one index rejects writes. Catalog keeps the index in step
        // one document at a time through this alias, so it has to stay writable.
        catalogReturns(2, "first");
        service.reindexProductsDetailed();

        ProductDocument document = new ProductDocument();
        document.setId("99");
        document.setProductId(99L);
        document.setSlug("slug-99");
        document.setName("added later");
        operations.save(document, IndexCoordinates.of(SearchIndexManager.ALIAS));
        assertThat(documentsBehindAlias()).isEqualTo(3);

        operations.delete("99", IndexCoordinates.of(SearchIndexManager.ALIAS));
        assertThat(documentsBehindAlias()).isEqualTo(2);
    }

    @Test
    void orphanedIndicesFromInterruptedRebuildsAreCleanedUp() {
        catalogReturns(1, "first");
        service.reindexProductsDetailed();
        String liveIndex = manager.currentIndex();

        // An index left behind by a rebuild that was killed before it could promote.
        String orphan = "products-1";
        var orphanOps = operations.indexOps(IndexCoordinates.of(orphan));
        orphanOps.create(ProductIndexSettings.settings());

        int dropped = manager.dropOrphanedIndices(liveIndex, 0L);

        assertThat(dropped).isGreaterThanOrEqualTo(1);
        assertThat(orphanOps.exists()).isFalse();
        assertThat(operations.indexOps(IndexCoordinates.of(liveIndex)).exists())
                .as("cleanup must never touch the index the alias points at")
                .isTrue();
    }
}
