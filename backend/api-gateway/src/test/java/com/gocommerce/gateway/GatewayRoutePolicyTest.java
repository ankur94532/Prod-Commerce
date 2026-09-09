package com.gocommerce.gateway;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRoutePolicyTest {
    @Test
    void expensiveAndMutatingRoutesAreRateLimited() {
        InputStream yaml = getClass().getResourceAsStream("/application.yml");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new Yaml().load(yaml);
        Map<String, Object> spring = map(root.get("spring"));
        Map<String, Object> cloud = map(spring.get("cloud"));
        Map<String, Object> gateway = map(cloud.get("gateway"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes = (List<Map<String, Object>>) gateway.get("routes");

        Set<String> limited = routes.stream()
                .filter(route -> hasFilter(route, "RequestRateLimiter"))
                .map(route -> String.valueOf(route.get("id")))
                .collect(Collectors.toSet());

        assertThat(limited).contains(
                "auth-service", "catalog-admin", "cart-service", "search-service-reindex",
                "search-service", "order-service", "analytics-service");
    }

    private boolean hasFilter(Map<String, Object> route, String name) {
        Object value = route.get("filters");
        if (!(value instanceof List<?> filters)) return false;
        return filters.stream().anyMatch(filter -> filter instanceof Map<?, ?> map && name.equals(map.get("name")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
