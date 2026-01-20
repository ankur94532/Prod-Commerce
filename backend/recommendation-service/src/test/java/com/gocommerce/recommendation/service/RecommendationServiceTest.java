package com.gocommerce.recommendation.service;

import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingProduct;
import com.gocommerce.recommendation.dto.RecommendationDtos.TrendingResponse;
import com.gocommerce.recommendation.events.OrderCreatedEvent;
import com.gocommerce.recommendation.model.ProductStats;
import com.gocommerce.recommendation.repository.ProductStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ProductStatsRepository productStatsRepository;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(productStatsRepository);
    }

    private OrderCreatedEvent sampleEvent() {
        OrderCreatedEvent.Line line = new OrderCreatedEvent.Line(
                "p1",
                "Product One",
                2,
                new BigDecimal("100.00")
        );
        return new OrderCreatedEvent(
                "order-1",
                "user-1",
                new BigDecimal("200.00"),
                "PAID",
                List.of(line)
        );
    }

    @Test
    void recordOrder_doesNothing_whenEventIsNull() {
        recommendationService.recordOrder(null);
        verifyNoInteractions(productStatsRepository);
    }

    @Test
    void recordOrder_doesNothing_whenItemsNull() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                "order-1", "user-1", BigDecimal.ZERO, "PAID", null
        );

        recommendationService.recordOrder(event);
        verifyNoInteractions(productStatsRepository);
    }

    @Test
    void recordOrder_createsNewStatsWhenMissing() {
        OrderCreatedEvent event = sampleEvent();

        when(productStatsRepository.findByProductId("p1"))
                .thenReturn(Optional.empty());

        recommendationService.recordOrder(event);

        ArgumentCaptor<ProductStats> captor = ArgumentCaptor.forClass(ProductStats.class);
        verify(productStatsRepository).save(captor.capture());

        ProductStats saved = captor.getValue();
        assertEquals("p1", saved.getProductId());
        assertEquals("Product One", saved.getProductName());
        assertEquals(2L, saved.getTotalQuantity());
        assertEquals(new BigDecimal("200.00"), saved.getTotalRevenue());
    }

    @Test
    void recordOrder_updatesExistingStats() {
        OrderCreatedEvent event = sampleEvent();

        ProductStats existing = new ProductStats(
                "p1",
                "Old Name",
                5,
                new BigDecimal("500.00")
        );

        when(productStatsRepository.findByProductId("p1"))
                .thenReturn(Optional.of(existing));

        recommendationService.recordOrder(event);

        ArgumentCaptor<ProductStats> captor = ArgumentCaptor.forClass(ProductStats.class);
        verify(productStatsRepository).save(captor.capture());

        ProductStats updated = captor.getValue();
        // 5 existing + 2 new
        assertEquals(7L, updated.getTotalQuantity());
        // 500 + 200
        assertEquals(new BigDecimal("700.00"), updated.getTotalRevenue());
        // latest name should be used
        assertEquals("Product One", updated.getProductName());
    }

    @Test
    void getTrending_limitsSizeAndMapsFields() {
        ProductStats s1 = new ProductStats("p1", "Product One", 10, new BigDecimal("1000.00"));
        ProductStats s2 = new ProductStats("p2", "Product Two", 8, new BigDecimal("800.00"));
        ProductStats s3 = new ProductStats("p3", "Product Three", 5, new BigDecimal("500.00"));

        when(productStatsRepository.findTop10ByOrderByTotalQuantityDesc())
                .thenReturn(List.of(s1, s2, s3));

        TrendingResponse response = recommendationService.getTrending(2);

        List<TrendingProduct> items = response.items();
        assertEquals(2, items.size());

        TrendingProduct first = items.get(0);
        assertEquals("p1", first.productId());
        assertEquals("Product One", first.productName());
        assertEquals(10L, first.totalQuantity());
        assertEquals(new BigDecimal("1000.00"), first.totalRevenue());
    }
}
