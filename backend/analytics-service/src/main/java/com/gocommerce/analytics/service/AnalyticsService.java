package com.gocommerce.analytics.service;

import com.gocommerce.analytics.metrics.AnalyticsMetrics;
import com.gocommerce.analytics.model.AnalyticsSummary;
import com.gocommerce.analytics.repository.AnalyticsSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;

@Service
public class AnalyticsService {

    private final AnalyticsSummaryRepository summaryRepository;
    private final AnalyticsMetrics analyticsMetrics;
    @Autowired
    public AnalyticsService(AnalyticsSummaryRepository summaryRepository,
                            AnalyticsMetrics analyticsMetrics) {
        this.summaryRepository = summaryRepository;
        this.analyticsMetrics = analyticsMetrics;
    }

    // For tests that don't care about metrics
    public AnalyticsService(AnalyticsSummaryRepository summaryRepository) {
        this(summaryRepository, null);
    }

    @Transactional
    public void recordOrder(BigDecimal totalAmount) {
        AnalyticsSummary summary = summaryRepository.findById(1L)
                .orElseGet(AnalyticsSummary::initial);

        summary.incrementOrder(totalAmount);
        summaryRepository.save(summary);

        if (analyticsMetrics != null) {
            analyticsMetrics.onOrderRecorded(totalAmount);
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary getSummary() {
        AnalyticsSummary summary = summaryRepository.findById(1L)
                .orElseGet(AnalyticsSummary::initial);

        if (analyticsMetrics != null) {
            analyticsMetrics.onSummaryRequested();
        }
        return summary;
    }

}
