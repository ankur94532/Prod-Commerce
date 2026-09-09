package com.gocommerce.catalog.config;

import com.gocommerce.platform.security.InternalServiceTokens;
import com.gocommerce.platform.security.JwtIssuer;
import com.gocommerce.platform.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the authorization rules themselves rather than the filter in isolation:
 * these are the boundaries that decide who may read the catalog and who may move stock.
 */
@SpringBootTest(classes = com.gocommerce.catalog.CatalogServiceApplication.class)
@AutoConfigureMockMvc
class CatalogSecurityRulesTest {

    private static final String SECRET = "test_secret_very_long_123456789012345";
    private static final String INTERNAL_TOKEN = "test_internal_service_token_1234567890";

    @Autowired
    private MockMvc mockMvc;

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
    void productBrowsingStaysPublic() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    }

    @Test
    void adminApisRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminApisRejectAnOrdinaryUserToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminApisAcceptAnAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void aRefreshTokenCannotReachAdminApis() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.refreshToken("user-1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inventoryMutationRequiresTheInternalServiceToken() throws Exception {
        mockMvc.perform(post("/api/v1/internal/inventory/products/1/decrement")
                .param("quantity", "1")
                .header("Idempotency-Key", "reservation-1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/internal/inventory/products/1/decrement")
                .param("quantity", "1")
                .header("Idempotency-Key", "reservation-1")
                .header(InternalServiceTokens.HEADER, "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aUserTokenIsNotASubstituteForTheInternalServiceToken() throws Exception {
        mockMvc.perform(post("/api/v1/internal/inventory/products/1/decrement")
                .param("quantity", "1")
                .header("Idempotency-Key", "reservation-1")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCorrectInternalTokenPassesTheGuard() throws Exception {
        // An unmapped path under the guarded prefix: 404 proves the filter let it through,
        // without depending on any inventory handler's behavior.
        mockMvc.perform(post("/api/v1/internal/ping").header(InternalServiceTokens.HEADER, INTERNAL_TOKEN))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/internal/ping"))
                .andExpect(status().isForbidden());
    }
}
