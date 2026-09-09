package com.gocommerce.search.client;

import com.gocommerce.search.config.RecommendationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RecommendationClientContractTest {

    @Test
    void parsesTheRecommendationPopularityContract() throws Exception {
        var restTemplate = new RestTemplate();
        var server = MockRestServiceServer.bindTo(restTemplate).build();
        var properties = new RecommendationProperties();
        properties.setBaseUrl("http://recommendation");
        var client = new RecommendationClient(restTemplate, properties, "test-internal");
        String fixture = Files.readString(Path.of("..", "..", "contracts", "search-recommendation", "popularity-response.json"));
        server.expect(requestTo("http://recommendation/internal/v1/recommendations/popularity?limit=1000"))
                .andExpect(header("X-Internal-Service-Token", "test-internal"))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        var popularity = client.fetchPopularity();

        assertThat(popularity).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo("42");
            assertThat(item.totalQuantity()).isEqualTo(11);
            assertThat(item.totalRevenue().toPlainString()).isEqualTo("14289.00");
        });
        server.verify();
    }
}
