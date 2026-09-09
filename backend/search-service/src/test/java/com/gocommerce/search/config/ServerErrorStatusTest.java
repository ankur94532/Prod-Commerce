package com.gocommerce.search.config;

import com.gocommerce.platform.security.InternalServiceTokens;
import com.gocommerce.search.SearchServiceApplication;
import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.repository.ProductSearchRepository;
import com.gocommerce.search.service.ProductEmbeddingService;
import com.gocommerce.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A chaos drill found that pausing Elasticsearch made search answer 401. Spring Security
 * filters the ERROR dispatch as well as the request, so an unpermitted /error turned every
 * server error into an auth failure: misleading to clients, and invisible to the
 * availability alert, which matches 5xx.
 *
 * MockMvc cannot catch this -- it rethrows the handler exception instead of dispatching --
 * so this runs a real server.
 */
@SpringBootTest(
        classes = SearchServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "search.bootstrap.reindex-empty-on-startup=false")
class ServerErrorStatusTest {

    private static final String INTERNAL_TOKEN = "test_internal_service_token_1234567890";

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private SearchService searchService;

    @MockBean
    private ProductSearchRepository productSearchRepository;

    @MockBean
    private SearchCache searchCache;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private ProductEmbeddingService productEmbeddingService;

    @Test
    void aDependencyFailureIsReportedAsAServerErrorNotAnAuthFailure() {
        when(searchService.reindexProductsDetailed()).thenThrow(new IllegalStateException("elasticsearch down"));
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalServiceTokens.HEADER, INTERNAL_TOKEN);

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/search/reindex", HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value())
                .as("a failing dependency must not look like an authentication problem")
                .isNotIn(401, 403);
        assertThat(response.getStatusCode().is5xxServerError())
                .as("status was %s; the availability alert only counts 5xx", response.getStatusCode())
                .isTrue();
    }

    @Test
    void anUnauthenticatedRequestStillReceivesFourZeroOne() {
        // The fix must not make everything permitted.
        ResponseEntity<String> response = rest.postForEntity("/api/v1/search/reindex", null, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
