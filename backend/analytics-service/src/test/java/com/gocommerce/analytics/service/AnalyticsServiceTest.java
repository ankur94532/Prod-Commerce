package com.gocommerce.analytics.service;

import com.gocommerce.analytics.model.AnalyticsSummary;
import com.gocommerce.analytics.repository.AnalyticsSummaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    @Test
    @DisplayName("getSummary returns existing summary if present")
    void getSummary_returnsExistingSummary() {
        AnalyticsSummary existing = AnalyticsSummary.initial();
        existing.incrementOrder(new BigDecimal("10.00"));

        AnalyticsSummaryRepository repo = mock(AnalyticsSummaryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        AnalyticsService service = new AnalyticsService(repo);

        AnalyticsSummary result = service.getSummary();

        assertThat(result.getTotalOrders()).isEqualTo(1L);
        assertThat(result.getTotalRevenue()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("getSummary returns initial zero summary when none exists")
    void getSummary_returnsInitialSummaryWhenNoneExists() {
        AnalyticsSummaryRepository repo = mock(AnalyticsSummaryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.empty());

        AnalyticsService service = new AnalyticsService(repo);

        AnalyticsSummary result = service.getSummary();

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTotalOrders()).isZero();
        assertThat(result.getTotalRevenue()).isEqualByComparingTo("0.00");
    }
}
