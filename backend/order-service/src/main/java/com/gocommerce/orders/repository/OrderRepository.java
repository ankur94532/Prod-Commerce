package com.gocommerce.orders.repository;

import com.gocommerce.orders.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT * FROM orders WHERE workflow_version = 1
          AND ((status = 'PENDING_PAYMENT' AND updated_at < :abandonedBefore)
            OR (status = 'COMPENSATING' AND (next_recovery_at IS NULL OR next_recovery_at <= :now)))
        ORDER BY updated_at LIMIT 1 FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Order> lockNextRecovery(@org.springframework.data.repository.query.Param("abandonedBefore") java.time.Instant abandonedBefore,
                                    @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    /** Orders awaiting recovery. Backed by a gauge so a stuck backlog is visible, not silent. */
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT count(*) FROM orders WHERE status IN ('PENDING_PAYMENT', 'COMPENSATING')
        """, nativeQuery = true)
    long countAwaitingRecovery();

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(updated_at))), 0)
        FROM orders WHERE status IN ('PENDING_PAYMENT', 'COMPENSATING')
        """, nativeQuery = true)
    double oldestAwaitingRecoverySeconds();

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
