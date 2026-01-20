package com.gocommerce.analytics.web;

import com.gocommerce.analytics.AnalyticsServiceApplication;
import com.gocommerce.analytics.model.AnalyticsSummary;
import com.gocommerce.analytics.repository.AnalyticsSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AnalyticsServiceApplication.class)
@AutoConfigureMockMvc
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsSummaryRepository analyticsSummaryRepository;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("analytics-service:OK"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_returnsAggregates_forAdmin() throws Exception {
        AnalyticsSummary summary = AnalyticsSummary.initial();
        summary.incrementOrder(new BigDecimal("999.99"));

        when(analyticsSummaryRepository.findById(1L))
                .thenReturn(Optional.of(summary));

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(999.99));
    }

    @Test
    void summary_returnsForbidden_forAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void summary_returnsForbidden_forNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isForbidden());
    }
}
