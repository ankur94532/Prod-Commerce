package com.gocommerce.recommendation.web;

import com.gocommerce.recommendation.config.SecurityConfig;
import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingProduct;
import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingResponse;
import com.gocommerce.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RecommendationController.class)
@Import(SecurityConfig.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

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
        TrendingResponse response = new TrendingResponse(List.of(tp));

        when(recommendationService.getTrending(5)).thenReturn(response);

        mockMvc.perform(get("/api/v1/recommendations/trending")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value("p1"))
                .andExpect(jsonPath("$.items[0].productName").value("Product One"))
                .andExpect(jsonPath("$.items[0].totalQuantity").value(10));

        verify(recommendationService).getTrending(5);
    }

    @Test
    void trending_clampsLimitAbove20_to20() throws Exception {
        when(recommendationService.getTrending(20))
                .thenReturn(new TrendingResponse(List.of()));

        mockMvc.perform(get("/api/v1/recommendations/trending")
                        .param("limit", "100")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(recommendationService).getTrending(20);
    }

    @Test
    void trending_clampsNonPositiveLimit_toDefault5() throws Exception {
        when(recommendationService.getTrending(5))
                .thenReturn(new TrendingResponse(List.of()));

        mockMvc.perform(get("/api/v1/recommendations/trending")
                        .param("limit", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(recommendationService).getTrending(5);
    }
}
