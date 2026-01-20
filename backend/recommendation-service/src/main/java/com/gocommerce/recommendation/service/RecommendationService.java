package com.gocommerce.recommendation.service;

import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingProduct;
import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingResponse;
import com.gocommerce.recommendation.events.OrderCreatedEvent;
import com.gocommerce.recommendation.model.ProductStats;
import com.gocommerce.recommendation.repository.ProductStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RecommendationService {

    private final ProductStatsRepository productStatsRepository;

    public RecommendationService(ProductStatsRepository productStatsRepository) {
        this.productStatsRepository = productStatsRepository;
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
    }

    @Transactional(readOnly = true)
    public TrendingResponse getTrending(int limit) {
        // simple: DB query top 10, then trim in memory if caller wants less
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

        return new TrendingResponse(items);
    }
}
