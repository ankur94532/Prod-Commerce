package com.gocommerce.orders.metrics;

import com.gocommerce.orders.outbox.OutboxEventRepository;
import com.gocommerce.orders.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The alert rules in ops/prometheus/rules are only as good as these gauges, and the
 * queries behind them are native SQL. This exercises them against real PostgreSQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "CHECKOUT_TEST_DB_URL", matches = ".+")
class CheckoutBacklogMetricsReliabilityPostgresTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CHECKOUT_TEST_DB_URL") + "?currentSchema=orders_backlog");
        registry.add("spring.datasource.username", () -> "checkout_test");
        registry.add("spring.datasource.password", () -> "checkout_test");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "orders_backlog");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    OrderRepository orders;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    JdbcTemplate jdbc;

    MeterRegistry registry;
    CheckoutBacklogMetrics metrics;

    @BeforeEach
    void reset() {
        jdbc.execute("DELETE FROM outbox_events");
        jdbc.execute("DELETE FROM order_items");
        jdbc.execute("DELETE FROM orders");
        registry = new SimpleMeterRegistry();
        metrics = new CheckoutBacklogMetrics(orders, outbox, registry);
    }

    private double gauge(String name) {
        var found = registry.find(name).gauge();
        assertNotNull(found, name + " is not registered");
        return found.value();
    }

    private void insertOrder(String status, Instant updatedAt) {
        jdbc.update("""
                INSERT INTO orders (user_id, status, total_amount, currency, created_at, updated_at, workflow_version)
                VALUES (?, ?, ?, 'INR', ?, ?, 1)
                """, "user-1", status, 100, Timestamp.from(updatedAt), Timestamp.from(updatedAt));
    }

    private void insertOutboxEvent(Instant createdAt, Instant publishedAt) {
        jdbc.update("""
                INSERT INTO outbox_events
                  (id, aggregate_type, aggregate_id, event_type, payload, attempts, next_attempt_at, created_at, published_at)
                VALUES (?, 'Order', '1', 'OrderCreated', '{}', 0, ?, ?, ?)
                """, UUID.randomUUID().toString(), Timestamp.from(createdAt), Timestamp.from(createdAt),
                publishedAt == null ? null : Timestamp.from(publishedAt));
    }

    @Test
    void anEmptySystemReportsZeroBacklogRatherThanNothing() {
        metrics.sample();

        assertEquals(0d, gauge("order_recovery_backlog"));
        assertEquals(0d, gauge("order_recovery_oldest_seconds"));
        assertEquals(0d, gauge("order_outbox_pending"));
        assertEquals(0d, gauge("order_outbox_oldest_seconds"));
    }

    @Test
    void ordersAwaitingRecoveryAreCountedAndAged() {
        Instant old = Instant.now().minus(10, ChronoUnit.MINUTES);
        insertOrder("PENDING_PAYMENT", old);
        insertOrder("COMPENSATING", Instant.now().minus(1, ChronoUnit.MINUTES));
        insertOrder("PAID", old);
        insertOrder("CANCELLED", old);

        metrics.sample();

        assertEquals(2d, gauge("order_recovery_backlog"), "only unresolved states count");
        assertTrue(gauge("order_recovery_oldest_seconds") >= 590,
                "age tracks the oldest waiting order, was " + gauge("order_recovery_oldest_seconds"));
    }

    @Test
    void completedOrdersDoNotLookLikeABacklog() {
        insertOrder("PAID", Instant.now().minus(2, ChronoUnit.HOURS));

        metrics.sample();

        assertEquals(0d, gauge("order_recovery_backlog"));
        assertEquals(0d, gauge("order_recovery_oldest_seconds"));
    }

    @Test
    void onlyUnpublishedOutboxEventsCountTowardsLag() {
        Instant old = Instant.now().minus(5, ChronoUnit.MINUTES);
        insertOutboxEvent(old, null);
        insertOutboxEvent(Instant.now(), null);
        insertOutboxEvent(old, Instant.now());

        metrics.sample();

        assertEquals(2d, gauge("order_outbox_pending"));
        assertTrue(gauge("order_outbox_oldest_seconds") >= 290,
                "lag tracks the oldest unpublished event, was " + gauge("order_outbox_oldest_seconds"));
    }

    @Test
    void aDrainedOutboxReportsNoLag() {
        insertOutboxEvent(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now());

        metrics.sample();

        assertEquals(0d, gauge("order_outbox_pending"));
        assertEquals(0d, gauge("order_outbox_oldest_seconds"));
    }

    @Test
    void aFailedSampleKeepsTheLastReadingInsteadOfReportingAFalseZero() {
        OrderRepository flaky = org.mockito.Mockito.mock(OrderRepository.class);
        org.mockito.Mockito.when(flaky.countAwaitingRecovery())
                .thenReturn(3L)
                .thenThrow(new IllegalStateException("database unreachable"));
        org.mockito.Mockito.when(flaky.oldestAwaitingRecoverySeconds()).thenReturn(600d);
        MeterRegistry isolated = new SimpleMeterRegistry();
        CheckoutBacklogMetrics degraded = new CheckoutBacklogMetrics(flaky, outbox, isolated);

        degraded.sample();
        assertEquals(3d, isolated.find("order_recovery_backlog").gauge().value());

        degraded.sample();

        // Reporting zero here would silence the very alert this metric exists to raise.
        assertEquals(3d, isolated.find("order_recovery_backlog").gauge().value(),
                "an unreachable database must not look like an empty backlog");
    }
}
