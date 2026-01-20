package com.gocommerce.analytics.service;

import com.gocommerce.analytics.model.AnalyticsSummary;
import com.gocommerce.analytics.repository.AnalyticsSummaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    @Test
    @DisplayName("recordOrder creates a new summary if none exists")
    void recordOrder_createsNewSummaryIfNoneExists() {
        AnalyticsSummaryRepository repo = mock(AnalyticsSummaryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.empty());

        AnalyticsService service = new AnalyticsService(repo);

        service.recordOrder(new BigDecimal("100.50"));

        ArgumentCaptor<AnalyticsSummary> captor = ArgumentCaptor.forClass(AnalyticsSummary.class);
        verify(repo).save(captor.capture());

        AnalyticsSummary saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getTotalOrders()).isEqualTo(1L);
        assertThat(saved.getTotalRevenue()).isEqualByComparingTo("100.50");
    }

    @Test
    @DisplayName("recordOrder updates an existing summary")
    void recordOrder_updatesExistingSummary() {
        AnalyticsSummary existing = AnalyticsSummary.initial();
        existing.incrementOrder(new BigDecimal("50.00")); // orders=1, revenue=50

        AnalyticsSummaryRepository repo = mock(AnalyticsSummaryRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        AnalyticsService service = new AnalyticsService(repo);

        service.recordOrder(new BigDecimal("25.00"));

        ArgumentCaptor<AnalyticsSummary> captor = ArgumentCaptor.forClass(AnalyticsSummary.class);
        verify(repo).save(captor.capture());

        AnalyticsSummary saved = captor.getValue();
        assertThat(saved.getTotalOrders()).isEqualTo(2L);
        assertThat(saved.getTotalRevenue()).isEqualByComparingTo("75.00");
    }

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
