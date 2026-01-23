package com.gocommerce.recommendation.service;

import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingProduct;
import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingResponse;
import com.gocommerce.recommendation.dto.PopularityDtos.PopularityItem;
import com.gocommerce.recommendation.dto.PopularityDtos.PopularityResponse;
import com.gocommerce.recommendation.events.OrderCreatedEvent;
import com.gocommerce.recommendation.metrics.RecommendationMetrics;
import com.gocommerce.recommendation.model.ProductStats;
import com.gocommerce.recommendation.repository.ProductStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.List;

@Service
public class RecommendationService {

    private final ProductStatsRepository productStatsRepository;
    private final RecommendationMetrics recommendationMetrics;
    @Autowired
    public RecommendationService(ProductStatsRepository productStatsRepository,
                                 RecommendationMetrics recommendationMetrics) {
        this.productStatsRepository = productStatsRepository;
        this.recommendationMetrics = recommendationMetrics;
    }

    // For tests that only pass the repository
    public RecommendationService(ProductStatsRepository productStatsRepository) {
        this(productStatsRepository, null);
    }

    @Transactional
    public void recordOrder(OrderCreatedEvent event) {
        if (event == null || event.items() == null) {
            return;
        }

        for (OrderCreatedEvent.Line line : event.items()) {
            String productId = line.productId();
            String productName = line.productName();
            long qty = line.quantity();
            BigDecimal lineTotal = line.unitPrice() != null
                    ? line.unitPrice().multiply(BigDecimal.valueOf(qty))
                    : BigDecimal.ZERO;

            ProductStats stats = productStatsRepository.findByProductId(productId)
                    .orElseGet(() -> new ProductStats(productId, productName, 0, BigDecimal.ZERO));

            stats.addPurchase(qty, lineTotal, productName);

            productStatsRepository.save(stats);
        }

        if (recommendationMetrics != null) {
            recommendationMetrics.onOrderEventProcessed();
        }
    }

    @Transactional(readOnly = true)
    public PopularityResponse getPopularity(int limit) {
        if (limit <= 0) limit = 10;
        if (limit > 1000) limit = 1000;

        List<ProductStats> stats = productStatsRepository
                .findTop1000ByOrderByTotalQuantityDesc();

        List<PopularityItem> items = stats.stream()
                .limit(limit)
                .map(ps -> new PopularityItem(
                        ps.getProductId(),
                        ps.getTotalQuantity(),
                        ps.getTotalRevenue()
                ))
                .toList();

        return new PopularityResponse(items);
    }

    @Transactional(readOnly = true)
    public TrendingResponse getTrending(int limit) {
        List<ProductStats> all = productStatsRepository.findTop10ByOrderByTotalQuantityDesc();

        List<TrendingProduct> items = all.stream()
                .limit(limit)
                .map(ps -> new TrendingProduct(
                        ps.getProductId(),
                        ps.getProductName(),
                        ps.getTotalQuantity(),
                        ps.getTotalRevenue()
                ))
                .toList();

        if (recommendationMetrics != null) {
            recommendationMetrics.onTrendingRequested();
        }

        return new TrendingResponse(items);
    }
}
