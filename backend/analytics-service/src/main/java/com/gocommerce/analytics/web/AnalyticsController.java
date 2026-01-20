package com.gocommerce.analytics.web;

import com.gocommerce.analytics.dto.AnalyticsSummaryResponse;
import com.gocommerce.analytics.model.AnalyticsSummary;
import com.gocommerce.analytics.service.AnalyticsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/health")
    public String health() {
        return "analytics-service:OK";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public AnalyticsSummaryResponse getSummary() {
        log.info("Inside controller");
        AnalyticsSummary summary = analyticsService.getSummary();
        return new AnalyticsSummaryResponse(
                summary.getTotalOrders(),
                summary.getTotalRevenue());
    }
}
