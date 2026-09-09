package com.gocommerce.cart.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Serializes each cart's read-modify-write sequence across service replicas. */
@Component
public class CartMutationCoordinator {

    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(2);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public CartMutationCoordinator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public <T> T execute(String userId, Supplier<T> mutation) {
        String lockKey = "cart-mutation-lock:" + userId;
        String owner = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + ACQUIRE_TIMEOUT.toNanos();
        boolean acquired = false;
        do {
            acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, owner, LOCK_TTL));
            if (!acquired) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CartBusyException();
                }
            }
        } while (!acquired && System.nanoTime() < deadline);

        if (!acquired) {
            throw new CartBusyException();
        }
        try {
            return mutation.get();
        } finally {
            redis.execute(RELEASE, List.of(lockKey), owner);
        }
    }
}
