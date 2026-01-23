package com.gocommerce.recommendation.web;

import com.gocommerce.recommendation.dto.PopularityDtos.PopularityResponse;
import com.gocommerce.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/recommendations")
public class RecommendationInternalController {

    private final RecommendationService recommendationService;

    public RecommendationInternalController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    // GET /internal/v1/recommendations/popularity?limit=1000
    @GetMapping("/popularity")
    public PopularityResponse popularity(
            @RequestParam(name = "limit", required = false, defaultValue = "1000") int limit
    ) {
        return recommendationService.getPopularity(limit);
    }
}
