package com.gocommerce.recommendation.repository;

import com.gocommerce.recommendation.model.ProductStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductStatsRepository extends JpaRepository<ProductStats, Long> {

    Optional<ProductStats> findByProductId(String productId);

    List<ProductStats> findTop10ByOrderByTotalQuantityDesc();

    List<ProductStats> findTop1000ByOrderByTotalQuantityDesc();

}
