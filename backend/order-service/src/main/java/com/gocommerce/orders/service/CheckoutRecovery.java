package com.gocommerce.orders.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class CheckoutRecovery {
    private final OrderWorkflow workflow;
    private final long abandonAfterSeconds;
    public CheckoutRecovery(OrderWorkflow workflow, @Value("${orders.recovery.abandon-after-seconds:120}") long abandonAfterSeconds) {
        this.workflow = workflow;
        this.abandonAfterSeconds = Math.max(1, abandonAfterSeconds);
    }
    @Scheduled(fixedDelayString = "${orders.recovery.delay-ms:5000}")
    public void recover() {
        for (int i = 0; i < 25; i++) {
            Instant now = Instant.now();
            if (!workflow.recoverOne(now.minusSeconds(abandonAfterSeconds), now)) break;
        }
    }
}
