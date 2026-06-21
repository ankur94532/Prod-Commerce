package com.gocommerce.search.config;

import com.gocommerce.search.service.ProductEmbeddingService;
import com.gocommerce.search.service.RemoteProductEmbeddingService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("embeddingService")
public class EmbeddingHealthIndicator implements HealthIndicator {

    private final ProductEmbeddingService embeddingService;

    public EmbeddingHealthIndicator(ProductEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("provider", embeddingService.getClass().getSimpleName())
                .withDetail("dimensions", embeddingService.dimensions());

        if (embeddingService instanceof RemoteProductEmbeddingService remote) {
            RemoteProductEmbeddingService.HealthStatus status = remote.healthStatus();
            builder.withDetail("baseUrl", status.baseUrl())
                    .withDetail("model", status.model())
                    .withDetail("remoteStatus", status.status());
            if (!status.available()) {
                return Health.down()
                        .withDetail("provider", embeddingService.getClass().getSimpleName())
                        .withDetail("dimensions", embeddingService.dimensions())
                        .withDetail("baseUrl", status.baseUrl())
                        .withDetail("remoteStatus", status.status())
                        .withDetail("error", status.message())
                        .build();
            }
        }

        return builder.build();
    }
}
