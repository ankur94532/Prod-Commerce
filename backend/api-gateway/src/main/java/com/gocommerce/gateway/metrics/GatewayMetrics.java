package com.gocommerce.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

    private final Counter routedRequests;

    public GatewayMetrics(MeterRegistry registry) {
        this.routedRequests = Counter.builder("gateway_requests_total")
                .description("Requests routed through API Gateway")
                .tag("service", "api-gateway")
                .register(registry);
    }

    public void onRoutedRequest() {
        routedRequests.increment();
    }
}
