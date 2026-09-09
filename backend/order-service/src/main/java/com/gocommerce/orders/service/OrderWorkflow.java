package com.gocommerce.orders.service;

import com.gocommerce.orders.client.CatalogClient;
import com.gocommerce.orders.dto.OrderDtos.*;
import com.gocommerce.orders.metrics.OrderMetrics;
import com.gocommerce.orders.model.*;
import com.gocommerce.orders.outbox.OrderOutboxService;
import com.gocommerce.orders.payment.*;
import com.gocommerce.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class OrderWorkflow {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkflow.class);

    private final OrderRepository orders;
    private final CatalogClient catalog;
    private final PaymentProvider payment;
    private final OrderOutboxService outbox;
    private final OrderMetrics metrics;

    public OrderWorkflow(OrderRepository orders, CatalogClient catalog, PaymentProvider payment,
                         OrderOutboxService outbox, OrderMetrics metrics) {
        this.orders = orders;
        this.catalog = catalog;
        this.payment = payment;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional
    public OrderResponse process(Long orderId, PaymentDetails details, boolean creator) {
        Order order = orders.lockById(orderId).orElseThrow();
        if (!creator || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return OrderService.toResponse(order);
        }
        try {
            for (OrderItem item : order.getItems()) {
                catalog.decrementStock(item.getProductId(), item.getQuantity(), reservationId(order, item));
            }
            // Keyed on the order, so the retry policy on this call cannot charge twice.
            PaymentResult result = payment.charge(new PaymentChargeRequest(
                    chargeKey(order), order.getTotalAmount(), order.getCurrency(),
                    details == null ? null : details.paymentToken(), "Order " + order.getId()));
            if (!result.success()) {
                throw new IllegalStateException("Payment declined");
            }
            order.setPaymentProvider(result.provider());
            order.setPaymentTransactionId(result.transactionId());
        } catch (RuntimeException ex) {
            order.setStatus(OrderStatus.COMPENSATING);
            log.warn("Checkout requires compensation orderId={} cause={}", order.getId(), ex.getClass().getSimpleName());
            compensate(order);
            return OrderService.toResponse(order);
        }
        order.setStatus(OrderStatus.PAID);
        // If serialization/commit fails, the durable pending intent remains recoverable.
        outbox.enqueueOrderCreated(order);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        metrics.onOrderCompleted();
                    }
                });
        return OrderService.toResponse(order);
    }

    @Transactional
    public boolean recoverOne(Instant abandonedBefore, Instant now) {
        var next = orders.lockNextRecovery(abandonedBefore, now);
        if (next.isEmpty()) {
            return false;
        }
        Order order = next.get();
        order.setStatus(OrderStatus.COMPENSATING);
        compensate(order);
        return true;
    }

    /**
     * Releases stock and, if the customer was charged, gives the money back.
     *
     * <p>Recovery used to cancel an abandoned order without ever asking the provider whether
     * the charge went through. A charge whose response was lost is indistinguishable from one
     * that never happened, so that path could cancel an order and release its stock while the
     * customer had been charged for it. The provider is now the authority on what happened.
     */
    private void compensate(Order order) {
        boolean complete = true;

        Optional<PaymentResult> settled;
        try {
            settled = reconcileCharge(order);
        } catch (PaymentProviderUnavailableException ex) {
            // Not knowing is not the same as knowing there was no charge. Retry later
            // rather than cancelling an order that may have been paid for.
            log.warn("Cannot reach the payment provider to reconcile orderId={}; deferring", order.getId(), ex);
            order.scheduleRecovery();
            return;
        }

        if (settled.isPresent()) {
            order.setPaymentProvider(settled.get().provider());
            order.setPaymentTransactionId(settled.get().transactionId());
            try {
                PaymentResult refund = payment.refund(new PaymentRefundRequest(
                        refundKey(order), settled.get().transactionId(), order.getTotalAmount(),
                        order.getCurrency(), "Order " + order.getId() + " could not be completed"));
                if (!refund.success()) {
                    throw new PaymentProviderUnavailableException("Refund refused: " + refund.failureReason());
                }
                order.setPaymentRefundId(refund.transactionId());
                log.info("Refunded orderId={} transaction={} refund={}",
                        order.getId(), settled.get().transactionId(), refund.transactionId());
            } catch (RuntimeException ex) {
                // An unconfirmed refund must never look like a completed one.
                complete = false;
                log.error("Refund pending for orderId={} transaction={} attempt={}",
                        order.getId(), settled.get().transactionId(), order.getRecoveryAttempts() + 1, ex);
            }
        }

        // Release ALL planned lines, including a reserve whose response was lost or never arrived.
        for (OrderItem item : order.getItems()) {
            try {
                catalog.incrementStock(item.getProductId(), item.getQuantity(), reservationId(order, item));
            } catch (RuntimeException ex) {
                complete = false;
                log.warn("Inventory release pending orderId={} lineId={} attempt={} cause={}",
                        order.getId(), item.getId(), order.getRecoveryAttempts() + 1, ex.getClass().getSimpleName());
            }
        }

        if (complete) {
            order.setStatus(OrderStatus.CANCELLED);
        } else {
            order.scheduleRecovery();
        }
    }

    /**
     * What the provider says happened for this order's charge key. A transaction id already
     * recorded locally is authoritative on its own; otherwise the provider is asked.
     */
    private Optional<PaymentResult> reconcileCharge(Order order) {
        if (order.getPaymentTransactionId() != null && order.getPaymentRefundId() == null) {
            return Optional.of(PaymentResult.success(order.getPaymentProvider(), order.getPaymentTransactionId()));
        }
        if (order.getPaymentRefundId() != null) {
            return Optional.empty(); // Already refunded; nothing more to give back.
        }
        return payment.lookup(chargeKey(order)).filter(PaymentResult::success);
    }

    private String chargeKey(Order order) {
        return "order:" + order.getId() + ":charge";
    }

    private String refundKey(Order order) {
        return "order:" + order.getId() + ":refund";
    }

    private String reservationId(Order order, OrderItem item) {
        return "order:" + order.getId() + ":line:" + item.getId();
    }
}
