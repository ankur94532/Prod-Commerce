package com.gocommerce.recommendation.service;

import com.gocommerce.recommendation.events.OrderCreatedEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(OrderEventProjectorReliabilityPostgresTest.Config.class)
@EnabledIfEnvironmentVariable(named = "CHECKOUT_TEST_DB_URL", matches = ".+")
class OrderEventProjectorReliabilityPostgresTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("CHECKOUT_TEST_DB_URL") + "?currentSchema=recommendation_reliability", "checkout_test", "checkout_test");
        }
        @Bean(initMethod = "migrate") Flyway flyway(DataSource ds) {
            return Flyway.configure().dataSource(ds).schemas("recommendation_reliability").locations("classpath:db/migration").load();
        }
        @Bean @DependsOn("flyway") JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean OrderEventProjector service(JdbcTemplate jdbc) { return new OrderEventProjector(jdbc, new com.gocommerce.recommendation.metrics.RecommendationMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())); }
    }
    @Autowired OrderEventProjector service;
    @Autowired JdbcTemplate jdbc;

    void concurrent(int count, java.util.function.IntConsumer work) throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < count; i++) { final int n = i; futures.add(pool.submit(() -> work.accept(n))); }
            for (var f : futures) f.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    @BeforeEach void reset() { jdbc.execute("TRUNCATE processed_order_events, product_stats"); }
    OrderCreatedEvent event(String id) {
        return new OrderCreatedEvent(id, "u", new BigDecimal("20.00"), "PAID",
                List.of(new OrderCreatedEvent.Line("p", "Product", 2, BigDecimal.TEN)));
    }
    @Test void concurrentRedeliveryAndDifferentOrdersAreCountedExactlyOnce() throws Exception {
        concurrent(20, n -> service.recordOrder(event("same")));
        concurrent(20, n -> service.recordOrder(event("order" + n)));
        assertEquals(21, jdbc.queryForObject("SELECT count(*) FROM processed_order_events", Integer.class));
        assertEquals(42, jdbc.queryForObject("SELECT total_quantity FROM product_stats", Integer.class));
        assertEquals(0, new BigDecimal("420.00").compareTo(jdbc.queryForObject("SELECT total_revenue FROM product_stats", BigDecimal.class)));
    }
    @Test void aggregateFailureRollsBackInboxThenRedeliverySucceeds() {
        jdbc.update("INSERT INTO product_stats (product_id, product_name, total_quantity, total_revenue) VALUES ('p', 'P', 0, 99999999999999999.99)");
        assertThrows(RuntimeException.class, () -> service.recordOrder(event("failed")));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM processed_order_events", Integer.class));
        jdbc.execute("TRUNCATE product_stats");
        service.recordOrder(event("failed"));
        service.recordOrder(event("failed"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed_order_events", Integer.class));
        assertEquals(0, new BigDecimal("20.00").compareTo(jdbc.queryForObject("SELECT total_revenue FROM product_stats", BigDecimal.class)));
    }
    @Test void malformedEventsAreNotAcknowledgedAsProcessed() {
        assertThrows(IllegalArgumentException.class, () -> service.recordOrder(null));
        assertThrows(IllegalArgumentException.class, () -> service.recordOrder(new OrderCreatedEvent("bad", "u", BigDecimal.TEN, "CANCELLED", List.of())));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM processed_order_events", Integer.class));
    }

    @Test void laterLineFailureRollsBackEarlierAggregateUpdate() {
        jdbc.update("INSERT INTO product_stats (product_id, product_name, total_quantity, total_revenue) VALUES ('z', 'Overflow', 0, 99999999999999999.99)");
        var event = new OrderCreatedEvent("partial", "u", new BigDecimal("30.00"), "PAID", List.of(
                new OrderCreatedEvent.Line("a", "First", 2, BigDecimal.TEN),
                new OrderCreatedEvent.Line("z", "Last", 1, BigDecimal.TEN)));
        assertThrows(RuntimeException.class, () -> service.recordOrder(event));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM product_stats WHERE product_id='a'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM processed_order_events", Integer.class));
        jdbc.update("UPDATE product_stats SET total_revenue=0 WHERE product_id='z'");
        service.recordOrder(event);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM product_stats", Integer.class));
    }
}
