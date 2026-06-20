package com.gocommerce.orders.repository;

import com.gocommerce.orders.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
