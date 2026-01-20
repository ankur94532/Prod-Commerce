package com.gocommerce.analytics.service;

import com.gocommerce.analytics.model.AnalyticsSummary;
import com.gocommerce.analytics.repository.AnalyticsSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AnalyticsService {

    private final AnalyticsSummaryRepository summaryRepository;

    public AnalyticsService(AnalyticsSummaryRepository summaryRepository) {
        this.summaryRepository = summaryRepository;
    }

    @Transactional
    public void recordOrder(BigDecimal totalAmount) {
        AnalyticsSummary summary = summaryRepository.findById(1L)
                .orElseGet(AnalyticsSummary::initial);

        summary.incrementOrder(totalAmount);

        summaryRepository.save(summary);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary getSummary() {
        return summaryRepository.findById(1L)
                .orElseGet(AnalyticsSummary::initial);
    }

}
