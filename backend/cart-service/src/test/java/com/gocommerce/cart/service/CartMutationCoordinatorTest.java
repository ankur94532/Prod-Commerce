package com.gocommerce.cart.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartMutationCoordinatorTest {

    @Test
    @SuppressWarnings("unchecked")
    void lockOwnerRunsMutationAndReleasesWithCompareAndDelete() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        String result = new CartMutationCoordinator(redis).execute("user-1", () -> "done");

        assertThat(result).isEqualTo("done");
        verify(redis).execute(any(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptedWaitFailsWithoutRunningMutation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> new CartMutationCoordinator(redis).execute("user-1", () -> "must-not-run"))
                    .isInstanceOf(CartBusyException.class);
        } finally {
            Thread.interrupted();
        }
    }
}
