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

/**
 * What a result page is worth when most of the catalog is variants.
 *
 * The seeded catalog carries six near-identical rows for most products, and a page of ten
 * that spends six slots on colours of one backpack has high recall and almost no use. These
 * fixtures reproduce that shape -- one family of six, one of two, two singletons -- and
 * assert the two things collapsing has to get right: one hit per family, and a total that
 * counts families, because a total that counts documents promises pages that are empty when
 * the caller asks for them.
 */
@SpringJUnitConfig(SearchCollapseElasticsearchTest.Config.class)
@EnabledIfEnvironmentVariable(named = "SEARCH_TEST_ES_ADDRESS", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchCollapseElasticsearchTest {
    @Configuration
    static class Config extends ElasticsearchConfiguration {
        @Override public ClientConfiguration clientConfiguration() {
            return ClientConfiguration.builder().connectedTo(System.getenv("SEARCH_TEST_ES_ADDRESS")).build();
        }
    }

    @Autowired ElasticsearchOperations operations;
    final IndexCoordinates index = IndexCoordinates.of("collapse-test-" + UUID.randomUUID());
    SearchCache cache;
    ElasticsearchOperations routed;
    ProductEmbeddingService embedding;

    static List<Float> vector(float x, float y) {
        var values = new ArrayList<>(Collections.nCopies(ProductDocument.SEARCH_EMBEDDING_DIMENSIONS, 0f));
        values.set(0, x);
        values.set(1, y);
        return values;
    }

    ProductDocument product(int id, String family, String name, String searchText, List<Float> vector) {
        var doc = new ProductDocument(String.valueOf(id), "fixture-" + id, name, "Deterministic collapse fixture",
                "Acme", "bags-luggage", (long) id, BigDecimal.valueOf(1000 + id), "INR", List.of(), null, 5,
                "black", "backpack", "regular", null, null, "nylon", searchText, 0L);
        doc.setSearchEmbedding(vector);
        doc.setProductFamily(family);
        return doc;
    }

    @BeforeAll void createFixtureIndex() {
        var indexOps = operations.indexOps(index);
        indexOps.create(ProductIndexSettings.settings());
        indexOps.putMapping(operations.indexOps(ProductDocument.class).createMapping());
        var documents = new ArrayList<ProductDocument>();
        // Six colours of one backpack. Only the first mentions "backpack" twice, so lexical
        // scoring prefers it while the vector leg prefers the last -- the case where the two
        // retrieval legs disagree about which variant represents the family.
        documents.add(product(1, "metro-backpack", "Metro Backpack Black",
                "metro backpack black backpack commuter", vector(1f, 0f)));
        for (int i = 2; i <= 6; i++) {
            documents.add(product(i, "metro-backpack", "Metro Backpack Variant " + i,
                    "metro backpack commuter", vector(1f - (i * 0.05f), i * 0.05f)));
        }
        documents.add(product(7, "trail-backpack", "Trail Backpack Green", "trail backpack hiking", vector(.5f, .5f)));
        documents.add(product(8, "trail-backpack", "Trail Backpack Grey", "trail backpack hiking", vector(.45f, .55f)));
        documents.add(product(9, "sling-bag", "Sling Bag", "sling backpack strap", vector(.2f, .8f)));
        documents.add(product(10, "duffel-bag", "Duffel Bag", "duffel backpack gym", vector(.1f, .9f)));
        operations.save(documents, index);
        indexOps.refresh();
    }

    @AfterAll void removeFixtureIndex() {
        operations.indexOps(index).delete();
    }

    @BeforeEach void setupCollaborators() {
        routed = mock(ElasticsearchOperations.class);
        when(routed.search(any(org.springframework.data.elasticsearch.core.query.Query.class), eq(ProductDocument.class)))
                .thenAnswer(i -> operations.search(
                        (org.springframework.data.elasticsearch.core.query.Query) i.getArgument(0),
                        ProductDocument.class, index));
        cache = mock(SearchCache.class);
        when(cache.get(any())).thenReturn(Optional.empty());
        embedding = mock(ProductEmbeddingService.class);
        when(embedding.embed(anyString())).thenReturn(vector(1f, 0f));
    }

    SearchService service(boolean collapse) {
        var properties = new SearchProperties();
        properties.getCollapse().setEnabled(collapse);
        properties.getRrf().setCandidateWindow(20);
        return new SearchService(mock(ProductSearchRepository.class), cache, null, routed, null, embedding, properties);
    }

    private static List<String> families(SearchResponse response, Map<String, String> familyById) {
        return response.items().stream().map(item -> familyById.get(item.id())).toList();
    }

    private static final Map<String, String> FAMILY_BY_ID = Map.of(
            "1", "metro-backpack", "2", "metro-backpack", "3", "metro-backpack", "4", "metro-backpack",
            "5", "metro-backpack", "6", "metro-backpack", "7", "trail-backpack", "8", "trail-backpack",
            "9", "sling-bag", "10", "duffel-bag");

    @Test void withoutCollapsingOneFamilyEatsTheWholePage() {
        // The behaviour being fixed, asserted rather than described: switch collapsing off
        // and six of the ten slots are the same backpack in different colours.
        var response = service(false).search(new SearchRequest("backpack", null, "text", 0, 10));

        assertThat(response.items()).hasSize(10);
        assertThat(Collections.frequency(families(response, FAMILY_BY_ID), "metro-backpack")).isEqualTo(6);
        assertThat(response.total()).isEqualTo(10);
        assertThat(response.retrieval().collapseField()).isNull();
        assertThat(response.retrieval().totalRelation()).isEqualTo("exact");
    }

    @ParameterizedTest @ValueSource(strings = {"text", "hybrid", "vector", "vector_exact"})
    void collapsingReturnsOneHitPerFamily(String mode) {
        var response = service(true).search(new SearchRequest("backpack", null, mode, 0, 10));

        var returned = families(response, FAMILY_BY_ID);
        assertThat(returned).doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("metro-backpack", "trail-backpack", "sling-bag", "duffel-bag");
        assertThat(response.retrieval().collapseField()).isEqualTo("productFamily");
    }

    @Test void totalCountsFamiliesSoEveryPromisedPageHasResults() {
        // A total of ten with four collapsed hits would advertise a second page that comes
        // back empty. This is the assertion that catches that regression.
        var service = service(true);
        var page0 = service.search(new SearchRequest("backpack", null, "text", 0, 3));

        assertThat(page0.total()).isEqualTo(4);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.retrieval().totalRelation()).isEqualTo("collapsed_groups_approximate");

        var lastPage = service.search(new SearchRequest("backpack", null, "text", page0.totalPages() - 1, 3));
        assertThat(lastPage.items()).isNotEmpty();
    }

    @Test void reciprocalRankFusionCountsAFamilyOnceEvenWhenItsLegsDisagree() {
        // The lexical leg's representative of metro-backpack is document 1; the vector leg's
        // is a different variant. Fusing on the document id would put both in the union and
        // undo the collapsing exactly where the union is built.
        var response = service(true).search(new SearchRequest("backpack", null, "hybrid_rrf", 0, 10));

        assertThat(families(response, FAMILY_BY_ID)).doesNotHaveDuplicates();
        assertThat(response.total()).isEqualTo(4);
        assertThat(response.retrieval().totalRelation()).isEqualTo("collapsed_candidate_union");
    }

    @Test void collapsingKeepsTheBestMemberOfEachFamilyRatherThanAnArbitraryOne() {
        // Document 1 scores highest lexically within its family. Collapsing must surface the
        // family's best hit, not whichever document Elasticsearch happened to reach first.
        var response = service(true).search(new SearchRequest("backpack", null, "text", 0, 10));

        assertThat(response.items()).extracting(SearchResultItem::id).contains("1");
    }
}
