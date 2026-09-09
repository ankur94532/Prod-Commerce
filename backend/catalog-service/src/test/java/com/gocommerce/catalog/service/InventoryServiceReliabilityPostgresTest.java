package com.gocommerce.catalog.service;

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

@SpringJUnitConfig(InventoryServiceReliabilityPostgresTest.Config.class)
@EnabledIfEnvironmentVariable(named = "CHECKOUT_TEST_DB_URL", matches = ".+")
class InventoryServiceReliabilityPostgresTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("CHECKOUT_TEST_DB_URL") + "?currentSchema=catalog_reliability", "checkout_test", "checkout_test");
        }
        @Bean(initMethod = "migrate") Flyway flyway(DataSource ds) {
            return Flyway.configure().dataSource(ds).schemas("catalog_reliability").locations("classpath:db/migration").load();
        }
        @Bean @DependsOn("flyway") JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean InventoryService service(JdbcTemplate jdbc) { return new InventoryService(jdbc); }
    }
    @Autowired InventoryService service;
    @Autowired JdbcTemplate jdbc;

    void concurrent(int count, java.util.function.IntConsumer work) throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < count; i++) { final int n = i; futures.add(pool.submit(() -> work.accept(n))); }
            for (var f : futures) f.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE inventory_reservations, products CASCADE");
        jdbc.update("INSERT INTO products (id, slug, name, price, currency, stock_quantity, active) VALUES (1, 'p', 'P', 10, 'INR', 10, true)");
    }
    int stock() { return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id=1", Integer.class); }

    @Test void concurrentReserveAndDuplicateReleaseChangeStockOnce() throws Exception {
        concurrent(20, n -> service.decrementStock(1L, 3, "r"));
        assertEquals(7, stock());
        concurrent(20, n -> service.incrementStock(1L, 3, "r"));
        assertEquals(10, stock());
        assertThrows(RuntimeException.class, () -> service.decrementStock(1L, 3, "r"));
        assertEquals(10, stock());
    }
    @Test void releaseBeforeDelayedReserveDoesNotCreateOrLoseStock() {
        service.incrementStock(1L, 3, "r");
        assertThrows(RuntimeException.class, () -> service.decrementStock(1L, 3, "r"));
        assertEquals(10, stock());
    }
    @Test void failedReserveAndPayloadMismatchLeaveStockUnchanged() {
        assertThrows(RuntimeException.class, () -> service.decrementStock(1L, 11, "r"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inventory_reservations", Integer.class));
        service.decrementStock(1L, 3, "r");
        assertThrows(RuntimeException.class, () -> service.incrementStock(1L, 4, "r"));
        assertThrows(RuntimeException.class, () -> service.decrementStock(1L, 4, "r"));
        assertThrows(RuntimeException.class, () -> service.decrementStock(1L, -1, "bad"));
        assertEquals(7, stock());
    }
    @Test void simultaneousDistinctReservationsCannotOversell() throws Exception {
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        concurrent(20, n -> {
            try { service.decrementStock(1L, 3, "r" + n); successes.incrementAndGet(); }
            catch (com.gocommerce.catalog.exception.OutOfStockException expected) { }
        });
        assertEquals(3, successes.get());
        assertEquals(1, stock());
    }
    @Test void inactiveProductCannotBeReservedButCanBeReleased() {
        service.decrementStock(1L, 3, "r");
        jdbc.update("UPDATE products SET active=false WHERE id=1");
        assertThrows(RuntimeException.class, () -> service.decrementStock(1L, 1, "other"));
        service.incrementStock(1L, 3, "r");
        assertEquals(10, stock());
    }
    @Test void releaseFailureRollsBackLedgerAndCanBeRetried() {
        service.decrementStock(1L, 3, "r");
        jdbc.update("UPDATE products SET stock_quantity=2147483647 WHERE id=1");
        assertThrows(RuntimeException.class, () -> service.incrementStock(1L, 3, "r"));
        assertEquals("RESERVED", jdbc.queryForObject("SELECT state FROM inventory_reservations WHERE reservation_id='r'", String.class));
        jdbc.update("UPDATE products SET stock_quantity=7 WHERE id=1");
        service.incrementStock(1L, 3, "r");
        assertEquals(10, stock());
    }
}
