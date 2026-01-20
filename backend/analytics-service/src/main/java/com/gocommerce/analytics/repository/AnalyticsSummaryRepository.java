package com.gocommerce.analytics.repository;

import com.gocommerce.analytics.model.AnalyticsSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsSummaryRepository extends JpaRepository<AnalyticsSummary, Long> {
}
