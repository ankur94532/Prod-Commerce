package com.gocommerce.cart.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void health_returnsStatusAndService() {
        HealthController controller = new HealthController();

        Map<String, Object> result = controller.health();

        assertThat(result.get("status")).isEqualTo("OK");
        assertThat(result.get("service")).isEqualTo("cart-service");
    }
}
