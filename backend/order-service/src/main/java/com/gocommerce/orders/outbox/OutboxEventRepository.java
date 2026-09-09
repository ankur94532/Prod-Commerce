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

    /** Unpublished events. A growing backlog means downstream projections are falling behind. */
    @Query(value = "select count(*) from outbox_events where published_at is null", nativeQuery = true)
    long countUnpublished();

    /** Age of the oldest unpublished event, in seconds; zero when the outbox is drained. */
    @Query(value = """
           select coalesce(extract(epoch from (now() - min(created_at))), 0)
           from outbox_events where published_at is null
           """, nativeQuery = true)
    double oldestUnpublishedSeconds();
}
