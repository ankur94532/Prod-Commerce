package com.gocommerce.recommendation.web;

import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingProduct;
import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingResponse;
import com.gocommerce.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecommendationControllerTest {

    private static class RecordingRecommendationService extends RecommendationService {
        int lastLimit;
        TrendingResponse responseToReturn;

        RecordingRecommendationService() {
            super(null); // repository not used in these tests
        }

        @Override
        public TrendingResponse getTrending(int limit) {
            this.lastLimit = limit;
            return responseToReturn != null ? responseToReturn : new TrendingResponse(List.of());
        }
    }

    private MockMvc mockMvc;
    private RecordingRecommendationService stubService;

    @BeforeEach
    void setUp() {
        stubService = new RecordingRecommendationService();
        RecommendationController controller = new RecommendationController(stubService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void health_returnsOkString() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("recommendation-service:OK"));
    }

    @Test
    void trending_usesDefaultLimitFive_andReturnsItems() throws Exception {
        TrendingProduct tp = new TrendingProduct(
                "p1",
                "Product One",
                10L,
                new BigDecimal("1000.00")
        );
        stubService.responseToReturn = new TrendingResponse(List.of(tp));

        mockMvc.perform(get("/api/v1/recommendations/trending")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value("p1"))
                .andExpect(jsonPath("$.items[0].productName").value("Product One"))
                .andExpect(jsonPath("$.items[0].totalQuantity").value(10));

        // default limit should be 5
        org.junit.jupiter.api.Assertions.assertEquals(5, stubService.lastLimit);
    }

    @Test
    void trending_clampsLimitAbove20_to20() throws Exception {
        stubService.responseToReturn = new TrendingResponse(List.of());

        mockMvc.perform(get("/api/v1/recommendations/trending")
                        .param("limit", "100")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(20, stubService.lastLimit);
    }

    @Test
    void trending_clampsNonPositiveLimit_toDefault5() throws Exception {
        stubService.responseToReturn = new TrendingResponse(List.of());

        mockMvc.perform(get("/api/v1/recommendations/trending")
                        .param("limit", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(5, stubService.lastLimit);
    }
}
