package com.gocommerce.search.client;

import com.gocommerce.search.config.RecommendationProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class RecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RecommendationClient(RestTemplate restTemplate, RecommendationProperties props) {
        this.restTemplate = restTemplate;
        this.baseUrl = props.getBaseUrl();
    }

    @CircuitBreaker(name = "recommendationClient", fallbackMethod = "fetchPopularityFallback")
    @Retry(name = "recommendationClient")
    public List<PopularityItem> fetchPopularity() {
        String url = baseUrl + "/internal/v1/recommendations/popularity?limit=1000";

        PopularityResponse response = restTemplate.getForObject(url, PopularityResponse.class);
        if (response == null || response.items() == null) {
            return Collections.emptyList();
        }
        return response.items();
    }

    @SuppressWarnings("unused")
    public List<PopularityItem> fetchPopularityFallback(Throwable ex) {
        log.warn("RecommendationClient.fetchPopularity fallback triggered, returning empty popularity boost list: {}",
                ex.toString());
        return Collections.emptyList();
    }

    // Local DTOs matching recommendation-service JSON
    public record PopularityItem(
            String productId,
            long totalQuantity,
            BigDecimal totalRevenue
    ) {}

    public record PopularityResponse(
            List<PopularityItem> items
    ) {}
}
