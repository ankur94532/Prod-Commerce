package com.gocommerce.orders.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select e from OutboxEvent e
           where e.publishedAt is null and e.nextAttemptAt <= :now
           order by e.createdAt asc
           """)
    List<OutboxEvent> findDueForPublishing(@Param("now") Instant now);
}
