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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecommendationService {

    private final ProductStatsRepository productStatsRepository;
    private final RecommendationMetrics recommendationMetrics;
    private final CatalogFallbackClient catalogFallbackClient;

    @Autowired
    public RecommendationService(ProductStatsRepository productStatsRepository,
                                 RecommendationMetrics recommendationMetrics,
                                 CatalogFallbackClient catalogFallbackClient) {
        this.productStatsRepository = productStatsRepository;
        this.recommendationMetrics = recommendationMetrics;
        this.catalogFallbackClient = catalogFallbackClient;
    }

    public RecommendationService(ProductStatsRepository productStatsRepository,
                                 RecommendationMetrics recommendationMetrics) {
        this(productStatsRepository, recommendationMetrics, null);
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

        List<TrendingProduct> items = new ArrayList<>(all.stream()
                .limit(limit)
                .map(ps -> new TrendingProduct(
                        ps.getProductId(),
                        ps.getProductName(),
                        ps.getTotalQuantity(),
                        ps.getTotalRevenue()
                ))
                .toList());

        fillTrendingFromCatalog(items, limit);

        if (recommendationMetrics != null) {
            recommendationMetrics.onTrendingRequested();
        }

        return new TrendingResponse(items);
    }

    private void fillTrendingFromCatalog(List<TrendingProduct> items, int limit) {
        if (catalogFallbackClient == null || items.size() >= limit) {
            return;
        }

        Set<String> existingProductIds = new HashSet<>();
        for (TrendingProduct item : items) {
            existingProductIds.add(item.productId());
        }

        List<CatalogFallbackClient.CatalogProduct> fallbackProducts;
        try {
            fallbackProducts = catalogFallbackClient.fetchProducts(limit);
        } catch (Exception ex) {
            return;
        }

        for (CatalogFallbackClient.CatalogProduct product : diverseFallbackProducts(fallbackProducts)) {
            if (items.size() >= limit) {
                return;
            }
            if (!existingProductIds.add(product.productId())) {
                continue;
            }
            items.add(new TrendingProduct(
                    product.productId(),
                    product.productName(),
                    0,
                BigDecimal.ZERO));
        }
    }

    private List<CatalogFallbackClient.CatalogProduct> diverseFallbackProducts(
            List<CatalogFallbackClient.CatalogProduct> products) {
        if (products == null || products.size() <= 1) {
            return products != null ? products : List.of();
        }

        List<CatalogFallbackClient.CatalogProduct> sorted = new ArrayList<>(products);
        sorted.sort(Comparator.comparing(
                product -> product.categorySlug() != null ? product.categorySlug() : ""));

        List<CatalogFallbackClient.CatalogProduct> selected = new ArrayList<>();
        Set<String> usedCategories = new HashSet<>();
        for (CatalogFallbackClient.CatalogProduct product : sorted) {
            String category = product.categorySlug();
            if (category != null && usedCategories.add(category)) {
                selected.add(product);
            }
        }
        for (CatalogFallbackClient.CatalogProduct product : products) {
            if (!selected.contains(product)) {
                selected.add(product);
            }
        }
        return selected;
    }
}
