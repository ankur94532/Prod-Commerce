package com.gocommerce.search.config;

import com.gocommerce.platform.security.InternalServiceTokens;
import com.gocommerce.platform.security.JwtIssuer;
import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.search.SearchServiceApplication;
import com.gocommerce.search.cache.SearchCache;
import com.gocommerce.search.repository.ProductSearchRepository;
import com.gocommerce.search.service.ProductEmbeddingService;
import com.gocommerce.search.service.ReindexResult;
import com.gocommerce.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * These endpoints were reachable by anyone before this configuration existed: reindex
 * deletes and recreates the live index, and the single-document endpoints can remove a
 * product from search results or inject one that is not in the catalog.
 */
@SpringBootTest(
        classes = SearchServiceApplication.class,
        properties = "search.bootstrap.reindex-empty-on-startup=false")
@AutoConfigureMockMvc
class SearchSecurityRulesTest {

    private static final String SECRET = "test_search_secret_very_long_123456789012";
    private static final String INTERNAL_TOKEN = "test_internal_service_token_1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    // Elasticsearch and Redis are not part of what this test asserts.
    @MockBean
    private ProductSearchRepository productSearchRepository;

    @MockBean
    private SearchCache searchCache;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private ProductEmbeddingService productEmbeddingService;

    private final JwtIssuer issuer = issuer();

    private static JwtIssuer issuer() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        return new JwtIssuer(properties);
    }

    private String bearer(String role) {
        return "Bearer " + issuer.accessToken("user-1", "user@example.com", "User One", role);
    }

    @Test
    void searchingStaysPublic() throws Exception {
        mockMvc.perform(get("/api/v1/search/health")).andExpect(status().isOk());
    }

    @Test
    void reindexRejectsAnonymousCallersAndNeverRunsTheRebuild() throws Exception {
        mockMvc.perform(post("/api/v1/search/reindex")).andExpect(status().isUnauthorized());

        verify(searchService, never()).reindexProductsDetailed();
    }

    @Test
    void reindexRejectsAnOrdinaryShopperToken() throws Exception {
        mockMvc.perform(post("/api/v1/search/reindex").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isForbidden());

        verify(searchService, never()).reindexProductsDetailed();
    }

    @Test
    void reindexRejectsARefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/search/reindex")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.refreshToken("user-1")))
                .andExpect(status().isUnauthorized());

        verify(searchService, never()).reindexProductsDetailed();
    }

    @Test
    void reindexRejectsAWrongInternalToken() throws Exception {
        mockMvc.perform(post("/api/v1/search/reindex").header(InternalServiceTokens.HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());

        verify(searchService, never()).reindexProductsDetailed();
    }

    @Test
    void reindexAcceptsAnAdministrator() throws Exception {
        when(searchService.reindexProductsDetailed()).thenReturn(ReindexResult.success(1, 1, 1));

        mockMvc.perform(post("/api/v1/search/reindex").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk());

        verify(searchService).reindexProductsDetailed();
    }

    @Test
    void reindexAcceptsTheInternalServiceToken() throws Exception {
        when(searchService.reindexProductsDetailed()).thenReturn(ReindexResult.success(1, 1, 1));

        mockMvc.perform(post("/api/v1/search/reindex").header(InternalServiceTokens.HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk());

        verify(searchService).reindexProductsDetailed();
    }

    @Test
    void indexingASingleProductIsServiceToServiceOnly() throws Exception {
        mockMvc.perform(post("/api/v1/search/index-product")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"p1\"}"))
                .andExpect(status().isUnauthorized());

        // Even an administrator does not write single documents; catalog owns that path.
        mockMvc.perform(post("/api/v1/search/index-product")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"p1\"}"))
                .andExpect(status().isForbidden());

        verify(searchService, never()).indexProductFromPayload(any());

        mockMvc.perform(post("/api/v1/search/index-product")
                .header(InternalServiceTokens.HEADER, INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"p1\"}"))
                .andExpect(status().isOk());

        verify(searchService).indexProductFromPayload(any());
    }

    @Test
    void removingAProductFromTheIndexIsServiceToServiceOnly() throws Exception {
        mockMvc.perform(delete("/api/v1/search/products/p1")).andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/search/products/p1").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isForbidden());

        verify(searchService, never()).deleteProductFromIndex(anyString());

        mockMvc.perform(delete("/api/v1/search/products/p1").header(InternalServiceTokens.HEADER, INTERNAL_TOKEN))
                .andExpect(status().isNoContent());

        verify(searchService).deleteProductFromIndex("p1");
    }
}
