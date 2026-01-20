package com.gocommerce.analytics.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsSummaryTest {

    @Test
    void initial_createsSummaryWithZeroValues() {
        AnalyticsSummary summary = AnalyticsSummary.initial();

        assertThat(summary.getId()).isEqualTo(1L);
        assertThat(summary.getTotalOrders()).isZero();
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void incrementOrder_incrementsOrdersAndRevenue() {
        AnalyticsSummary summary = AnalyticsSummary.initial();

        summary.incrementOrder(new BigDecimal("42.50"));

        assertThat(summary.getTotalOrders()).isEqualTo(1L);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("42.50");
    }

    @Test
    void incrementOrder_handlesNullAmountAsZero() {
        AnalyticsSummary summary = AnalyticsSummary.initial();

        summary.incrementOrder(null);

        assertThat(summary.getTotalOrders()).isEqualTo(1L);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo("0.00");
    }
}
