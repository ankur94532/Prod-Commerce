package com.gocommerce.search.config;

import java.util.Map;

public final class ProductIndexSettings {

    private ProductIndexSettings() {
    }

    public static Map<String, Object> settings() {
        return Map.of(
                "index", Map.of(
                        "number_of_shards", 1,
                        "number_of_replicas", 0));
    }
}
