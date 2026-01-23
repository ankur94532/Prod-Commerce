package com.gocommerce.recommendation.web;

import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingResponse;
import com.gocommerce.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/health")
    public String health() {
        return "recommendation-service:OK";
    }

    // GET /api/v1/recommendations/trending?limit=5
    @GetMapping("/trending")
    public TrendingResponse trending(
            @RequestParam(name = "limit", required = false, defaultValue = "5") int limit
    ) {
        if (limit <= 0) limit = 5;
        if (limit > 20) limit = 20;

        return recommendationService.getTrending(limit);
    }
}
