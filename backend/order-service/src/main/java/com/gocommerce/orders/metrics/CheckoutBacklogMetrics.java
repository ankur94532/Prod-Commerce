package com.gocommerce.orders.metrics;

import com.gocommerce.orders.outbox.OutboxEventRepository;
import com.gocommerce.orders.repository.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Recovery and outbox backlogs were only visible by running SQL by hand, which means a
 * stuck order or an outbox that stopped draining produced no signal at all. These gauges
 * are what the alert rules in ops/prometheus/rules/ watch.
 *
 * The counts are sampled on a schedule rather than computed per scrape, so a slow query
 * cannot stall the metrics endpoint.
 */
@Component
public class CheckoutBacklogMetrics {

    private static final Logger log = LoggerFactory.getLogger(CheckoutBacklogMetrics.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;

    private final AtomicLong recoveryBacklog = new AtomicLong();
    private final AtomicLong recoveryOldestSeconds = new AtomicLong();
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicLong outboxOldestSeconds = new AtomicLong();

    public CheckoutBacklogMetrics(OrderRepository orderRepository, OutboxEventRepository outboxRepository,
                                  MeterRegistry registry) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;

        Gauge.builder("order_recovery_backlog", recoveryBacklog, AtomicLong::doubleValue)
                .description("Orders in PENDING_PAYMENT or COMPENSATING awaiting recovery")
                .register(registry);
        Gauge.builder("order_recovery_oldest_seconds", recoveryOldestSeconds, AtomicLong::doubleValue)
                .description("Age of the oldest order awaiting recovery")
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder("order_outbox_pending", outboxBacklog, AtomicLong::doubleValue)
                .description("Outbox events not yet published to Kafka")
                .register(registry);
        Gauge.builder("order_outbox_oldest_seconds", outboxOldestSeconds, AtomicLong::doubleValue)
                .description("Age of the oldest unpublished outbox event")
                .baseUnit("seconds")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${orders.metrics.backlog-sample-delay-ms:15000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sample() {
        try {
            recoveryBacklog.set(orderRepository.countAwaitingRecovery());
            recoveryOldestSeconds.set((long) orderRepository.oldestAwaitingRecoverySeconds());
            outboxBacklog.set(outboxRepository.countUnpublished());
            outboxOldestSeconds.set((long) outboxRepository.oldestUnpublishedSeconds());
        } catch (RuntimeException error) {
            // Leave the previous readings in place rather than reporting a false zero,
            // which would silence exactly the alert this exists to raise.
            log.warn("Could not sample checkout backlog metrics: {}", error.toString());
        }
    }
}
