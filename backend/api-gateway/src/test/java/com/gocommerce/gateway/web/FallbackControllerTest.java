package com.gocommerce.gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FallbackControllerTest {

    @Test
    void searchDependencyFailureIsVisibleAsServiceUnavailable() {
        ResponseEntity<Map<String, Object>> response =
                new FallbackController().searchFallback().block();

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("fallback"));
        assertEquals("api-gateway-circuit-breaker", response.getBody().get("source"));
    }
}
