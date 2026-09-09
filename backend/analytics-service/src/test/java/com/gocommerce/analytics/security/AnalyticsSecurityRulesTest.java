package com.gocommerce.analytics.security;

import com.gocommerce.platform.security.JwtIssuer;
import com.gocommerce.platform.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.gocommerce.analytics.AnalyticsServiceApplication.class)
@AutoConfigureMockMvc
class AnalyticsSecurityRulesTest {

    private static final String SECRET = "test_secret_very_long_123456789012345";

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
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/health")).andExpect(status().isOk());
    }

    @Test
    void businessAggregatesRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void businessAggregatesRejectOrdinaryShoppers() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRefreshTokenCannotReadBusinessAggregates() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.refreshToken("user-1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorsCanReadBusinessAggregates() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk());
    }
}
