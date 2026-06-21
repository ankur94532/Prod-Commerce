package com.gocommerce.search.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class SearchCacheRedis implements SearchCache {

    private static final Logger log = LoggerFactory.getLogger(SearchCacheRedis.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public SearchCacheRedis(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${search.cache.ttl-seconds:60}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Optional<SearchResponse> get(SearchRequest request) {
        String key = buildKey(request);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            SearchResponse response = objectMapper.readValue(json, SearchResponse.class);
            return Optional.of(response);
        } catch (IOException e) {
            log.warn("Failed to deserialize search cache entry for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(SearchRequest request, SearchResponse response) {
        String key = buildKey(request);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize search cache entry for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void clear() {
        Set<String> keys = redisTemplate.keys("search:*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
        log.info("Cleared {} search cache entries", keys.size());
    }

    private String buildKey(SearchRequest request) {
        String q = normalized(request.query());
        String category = normalized(request.category());
        String brand = normalized(request.brand());
        String minPrice = request.minPrice() != null ? request.minPrice().toPlainString() : "";
        String maxPrice = request.maxPrice() != null ? request.maxPrice().toPlainString() : "";
        String inStock = request.inStock() != null ? String.valueOf(request.inStock()) : "";
        String color = normalized(request.color());
        String type = normalized(request.type());
        String fit = normalized(request.fit());
        String storage = normalized(request.storage());
        String memory = normalized(request.memory());
        String material = normalized(request.material());
        String sort = normalized(request.sort());
        String mode = normalized(request.mode());
        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 20;

        return String.format(
                "search:q=%s|cat=%s|brand=%s|min=%s|max=%s|stock=%s|color=%s|type=%s|fit=%s|storage=%s|memory=%s|material=%s|sort=%s|mode=%s|p=%d|s=%d",
                q, category, brand, minPrice, maxPrice, inStock, color, type, fit, storage, memory, material, sort, mode, page, size);
    }

    private String normalized(String value) {
        return value != null ? value.trim().toLowerCase() : "";
    }
}
