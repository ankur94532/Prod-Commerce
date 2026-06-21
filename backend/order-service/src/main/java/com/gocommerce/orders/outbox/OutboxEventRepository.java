package com.gocommerce.orders.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * PostgreSQL-specific query so multiple order-service replicas can safely publish the outbox.
     * SKIP LOCKED prevents two pods from selecting the same unpublished event.
     */
    @Query(value = """
           select *
           from outbox_events
           where published_at is null
             and next_attempt_at <= :now
           order by created_at asc
           limit :limit
           for update skip locked
           """, nativeQuery = true)
    List<OutboxEvent> findDueForPublishing(@Param("now") Instant now, @Param("limit") int limit);
}
