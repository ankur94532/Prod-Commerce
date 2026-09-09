package com.gocommerce.recommendation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.recommendation.dto.PopularityDtos.PopularityResponse;
import com.gocommerce.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationInternalContractTest {

    @Test
    void producesThePopularityContractConsumedBySearchService() throws Exception {
        var mapper = new ObjectMapper();
        String fixture = Files.readString(Path.of("..", "..", "contracts", "search-recommendation", "popularity-response.json"));
        var service = mock(RecommendationService.class);
        when(service.getPopularity(1000)).thenReturn(mapper.readValue(fixture, PopularityResponse.class));
        var mvc = MockMvcBuilders.standaloneSetup(new RecommendationInternalController(service)).build();

        String response = mvc.perform(get("/internal/v1/recommendations/popularity"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(mapper.readTree(response)).isEqualTo(mapper.readTree(fixture));
    }
}
