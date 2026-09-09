package com.gocommerce.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.orders.client.CatalogClient;
import com.gocommerce.orders.dto.OrderDtos.*;
import com.gocommerce.orders.exception.IdempotencyConflictException;
import com.gocommerce.orders.metrics.OrderMetrics;
import com.gocommerce.orders.outbox.*;
import com.gocommerce.orders.payment.*;
import com.gocommerce.orders.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, OrderIntentService.class, OrderWorkflow.class, OrderOutboxService.class, ObjectMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "CHECKOUT_TEST_DB_URL", matches = ".+")
class CheckoutReliabilityPostgresTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("CHECKOUT_TEST_DB_URL") + "?currentSchema=orders_reliability");
        r.add("spring.datasource.username", () -> "checkout_test");
        r.add("spring.datasource.password", () -> "checkout_test");
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.schemas", () -> "orders_reliability");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.show-sql", () -> "false");
    }
    @Autowired OrderService service;
    @Autowired OrderIntentService intents;
    @Autowired OrderWorkflow workflow;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepository orders;
    @MockBean CatalogClient catalog;
    @MockBean PaymentProvider payment;
    @MockBean OrderMetrics metrics;
    @org.springframework.boot.test.mock.mockito.SpyBean OrderOutboxService outbox;

    // Fault-injecting remote boundary; real catalog SQL is independently tested in catalog-service.
    final Map<String, String> reservations = new ConcurrentHashMap<>();
    final AtomicBoolean releaseUnavailable = new AtomicBoolean();
    final AtomicBoolean lostReserveResponse = new AtomicBoolean();

    @BeforeEach void resetState() {
        jdbc.execute("TRUNCATE orders, order_items, outbox_events RESTART IDENTITY CASCADE");
        reset(catalog, payment, outbox);
        reservations.clear(); releaseUnavailable.set(false); lostReserveResponse.set(false);
        when(catalog.getProductSnapshot(anyString())).thenAnswer(i -> new CatalogClient.ProductSnapshot(i.getArgument(0), "Catalog price", new BigDecimal("100.00"), "INR"));
        when(payment.charge(any())).thenReturn(PaymentResult.success("mock", "tx"));
        doAnswer(i -> {
            String key = i.getArgument(2);
            if ("RELEASED".equals(reservations.get(key))) throw new IllegalStateException("Released");
            reservations.put(key, "RESERVED");
            if (lostReserveResponse.get()) throw new IllegalStateException("Lost reserve response");
            return null;
        }).when(catalog).decrementStock(anyString(), anyInt(), anyString());
        doAnswer(i -> {
            if (releaseUnavailable.get()) throw new IllegalStateException("Catalog down");
            reservations.put(i.getArgument(2), "RELEASED"); return null;
        }).when(catalog).incrementStock(anyString(), anyInt(), anyString());
    }
    CreateOrderRequest request(int quantity) {
        return new CreateOrderRequest("u", List.of(new CreateOrderItemRequest("1", "Tampered", quantity, BigDecimal.ONE),
                new CreateOrderItemRequest("2", null, 1, null)), new PaymentDetails("4242424242424242", "12/30", "123"));
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    String status() { return jdbc.queryForObject("SELECT status FROM orders", String.class); }
    void makeDue() { jdbc.update("UPDATE orders SET updated_at=now()-interval '10 minutes', next_recovery_at=now()-interval '1 minute'"); }
    void recover() { assertTrue(workflow.recoverOne(Instant.now().minusSeconds(120), Instant.now())); }

    @Test void concurrentDuplicateRequestsCreateOneOrderAndOneEvent() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<OrderResponse>>();
            for (int i=0;i<20;i++) futures.add(pool.submit(() -> service.createOrder(request(2), "same")));
            Set<Long> ids = new HashSet<>();
            for (var f : futures) ids.add(f.get(20, TimeUnit.SECONDS).id());
            assertEquals(1, ids.size());
        } finally { pool.shutdownNow(); }
        var response = service.createOrder(request(2), "same");
        assertEquals("PAID", response.status());
        assertEquals(new BigDecimal("300.00"), response.totalAmount());
        assertEquals("Catalog price", response.items().get(0).productName());
        assertEquals(1, count("orders")); assertEquals(1, count("outbox_events"));
        verify(payment, times(1)).charge(any());
        verify(catalog, times(2)).decrementStock(anyString(), anyInt(), anyString());
    }

    @Test void changedPayloadConflictsAndKeysAreScopedToUser() {
        service.createOrder(request(2), "same");
        assertThrows(IdempotencyConflictException.class, () -> service.createOrder(request(3), "same"));
        var r = request(2);
        service.createOrder(new CreateOrderRequest("other", r.items(), r.payment()), "same");
        assertEquals(2, count("orders"));
    }

    @Test void lostReserveResponseReleasesEveryPlannedLine() {
        lostReserveResponse.set(true);
        var response = service.createOrder(request(2), "lost");
        assertEquals("CANCELLED", response.status());
        assertEquals(2, reservations.size());
        assertTrue(reservations.values().stream().allMatch("RELEASED"::equals));
        verify(payment, never()).charge(any());  // compensation still asks whether a charge exists
        assertEquals(0, count("outbox_events"));
    }

    @Test void releaseOutagePersistsRecoveryAndRetryCompletesCancellation() {
        when(payment.charge(any())).thenReturn(PaymentResult.failure("mock", "declined"));
        releaseUnavailable.set(true);
        assertEquals("COMPENSATING", service.createOrder(request(2), "decline").status());
        assertEquals(1, jdbc.queryForObject("SELECT recovery_attempts FROM orders", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT next_recovery_at FROM orders", java.sql.Timestamp.class));
        makeDue(); recover();
        assertEquals(2, jdbc.queryForObject("SELECT recovery_attempts FROM orders", Integer.class));
        releaseUnavailable.set(false); makeDue(); recover();
        assertEquals("CANCELLED", status());
        assertTrue(reservations.values().stream().allMatch("RELEASED"::equals));
        assertEquals("CANCELLED", service.createOrder(request(2), "decline").status());
        verify(payment, times(1)).charge(any());
        assertEquals(0, count("outbox_events"));
    }

    @Test void abandonedCommittedIntentIsRecoveredWithoutOriginalRequestOrPayment() {
        var r = request(2);
        intents.prepare(r, "abandoned", OrderService.hashRequest(r));
        makeDue(); recover();
        assertEquals("CANCELLED", status());
        assertEquals(2, reservations.size());
        verify(payment, never()).charge(any());  // compensation still asks whether a charge exists
    }

    @Test void databaseFailureAfterRemoteEffectsLeavesRecoverableIntentAndNoEvent() {
        doThrow(new IllegalStateException("Outbox unavailable")).when(outbox).enqueueOrderCreated(any());
        assertThrows(IllegalStateException.class, () -> service.createOrder(request(2), "rollback"));
        assertEquals("PENDING_PAYMENT", status());
        assertEquals(0, count("outbox_events"));
        // Retry observes the pending state; it must never charge again.
        assertEquals("PENDING_PAYMENT", service.createOrder(request(2), "rollback").status());
        makeDue(); recover();
        assertEquals("CANCELLED", status());
        assertTrue(reservations.values().stream().allMatch("RELEASED"::equals));
        verify(payment, times(1)).charge(any());
    }

    @Test void recoverySkipsAnActivelyLockedCheckout() throws Exception {
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        when(payment.charge(any())).thenAnswer(i -> { entered.countDown(); assertTrue(finish.await(10, TimeUnit.SECONDS)); return PaymentResult.success("mock", "tx"); });
        var pool = Executors.newSingleThreadExecutor();
        try {
            var checkout = pool.submit(() -> service.createOrder(request(2), "active"));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertFalse(workflow.recoverOne(Instant.now().plusSeconds(60), Instant.now()));
            finish.countDown();
            assertEquals("PAID", checkout.get(10, TimeUnit.SECONDS).status());
        } finally { finish.countDown(); pool.shutdownNow(); }
    }

    @Test void legacyUnpaidOrdersAreNotReleasedUsingUnknownReservationIds() {
        var r = request(2); intents.prepare(r, "legacy", OrderService.hashRequest(r));
        jdbc.update("UPDATE orders SET workflow_version=0"); makeDue();
        assertFalse(workflow.recoverOne(Instant.now(), Instant.now()));
        assertEquals("PENDING_PAYMENT", status());
        verify(catalog, never()).incrementStock(anyString(), anyInt(), anyString());
    }

    @Test void legacyPaidReplayUsesOriginalFingerprintAndLookupIsUserScoped() {
        service.createOrder(request(2), "legacy-paid");
        jdbc.update("UPDATE orders SET workflow_version=0, idempotency_request_hash=?", OrderService.legacyHashRequest(request(2)));
        assertEquals("PAID", service.createOrder(request(2), "legacy-paid").status());
        assertThrows(IdempotencyConflictException.class, () -> service.createOrder(request(3), "legacy-paid"));
        assertEquals("PAID", service.findAttempt("u", "legacy-paid").status());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.findAttempt("other", "legacy-paid"));
        verify(payment, times(1)).charge(any());
    }

    @Test void laterLineFailureReleasesEarlierReservationWithoutCharging() {
        doThrow(new IllegalStateException("Out of stock")).when(catalog).decrementStock(eq("2"), anyInt(), anyString());
        assertEquals("CANCELLED", service.createOrder(request(2), "partial").status());
        assertEquals(2, reservations.size());
        assertTrue(reservations.values().stream().allMatch("RELEASED"::equals));
        verify(payment, never()).charge(any());  // compensation still asks whether a charge exists
    }

    @Test void concurrentRecoveryWorkersDoNotProcessTheSameOrder() throws Exception {
        var r = request(2); intents.prepare(r, "recover-once", OrderService.hashRequest(r)); makeDue();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> workflow.recoverOne(Instant.now(), Instant.now()));
            var two = pool.submit(() -> workflow.recoverOne(Instant.now(), Instant.now()));
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals("CANCELLED", status());
        verify(catalog, times(2)).incrementStock(anyString(), anyInt(), anyString());
    }

    // --- Payment reconciliation during recovery ---
    // Recovery previously cancelled an abandoned order without asking whether the charge
    // had gone through, which could release stock for an order the customer paid for.

    String refundId() { return jdbc.queryForObject("SELECT payment_refund_id FROM orders", String.class); }

    @Test void recoveryRefundsAnOrderWhoseChargeSucceededButWhoseResponseWasLost() {
        var r = request(2);
        intents.prepare(r, "lost-charge", OrderService.hashRequest(r));
        // The provider took the money; the caller never learned that.
        when(payment.lookup(anyString())).thenReturn(Optional.of(PaymentResult.success("mock", "tx-lost")));
        when(payment.refund(any())).thenReturn(PaymentResult.success("mock", "re-1"));
        makeDue();

        recover();

        assertEquals("CANCELLED", status());
        assertEquals("re-1", refundId());
        verify(payment, times(1)).refund(any());
    }

    @Test void recoveryDoesNotRefundAnOrderThatWasNeverCharged() {
        var r = request(2);
        intents.prepare(r, "never-charged", OrderService.hashRequest(r));
        when(payment.lookup(anyString())).thenReturn(Optional.empty());
        makeDue();

        recover();

        assertEquals("CANCELLED", status());
        assertNull(refundId());
        verify(payment, never()).refund(any());
    }

    @Test void anUnreachableProviderDefersRecoveryRatherThanCancellingAPossiblyPaidOrder() {
        var r = request(2);
        intents.prepare(r, "provider-down", OrderService.hashRequest(r));
        when(payment.lookup(anyString()))
                .thenThrow(new PaymentProviderUnavailableException("provider unreachable"));
        makeDue();

        recover();

        // Not knowing is not the same as knowing there was no charge.
        assertEquals("COMPENSATING", status());
        assertNull(refundId());
        verify(payment, never()).refund(any());
        verify(catalog, never()).incrementStock(anyString(), anyInt(), anyString());
    }

    @Test void anUnconfirmedRefundKeepsTheOrderInCompensationAndIsRetriedExactlyOnce() {
        var r = request(2);
        intents.prepare(r, "refund-retry", OrderService.hashRequest(r));
        when(payment.lookup(anyString())).thenReturn(Optional.of(PaymentResult.success("mock", "tx-retry")));
        when(payment.refund(any()))
                .thenThrow(new PaymentProviderUnavailableException("refund not confirmed"))
                .thenReturn(PaymentResult.success("mock", "re-2"));
        makeDue();

        recover();
        assertEquals("COMPENSATING", status());
        assertNull(refundId());

        makeDue();
        recover();

        assertEquals("CANCELLED", status());
        assertEquals("re-2", refundId());
        verify(payment, times(2)).refund(any());
    }

    @Test void aRefundedOrderIsNotRefundedAgainIfRecoveryRunsOnceMore() {
        var r = request(2);
        intents.prepare(r, "refund-once", OrderService.hashRequest(r));
        when(payment.lookup(anyString())).thenReturn(Optional.of(PaymentResult.success("mock", "tx-once")));
        when(payment.refund(any())).thenReturn(PaymentResult.success("mock", "re-3"));
        // Stock release fails the first time, so the order stays in compensation with the
        // refund already issued.
        releaseUnavailable.set(true);
        makeDue();
        recover();
        assertEquals("COMPENSATING", status());
        assertEquals("re-3", refundId());

        releaseUnavailable.set(false);
        makeDue();
        recover();

        assertEquals("CANCELLED", status());
        verify(payment, times(1)).refund(any());
    }

    @Test void theChargeIsKeyedOnTheOrderSoARetryCannotBillTwice() {
        service.createOrder(request(1), "keyed-charge");

        var request = org.mockito.ArgumentCaptor.forClass(PaymentChargeRequest.class);
        verify(payment).charge(request.capture());
        assertNotNull(request.getValue().idempotencyKey());
        assertTrue(request.getValue().idempotencyKey().startsWith("order:"));
        assertTrue(request.getValue().idempotencyKey().endsWith(":charge"));
    }
}
