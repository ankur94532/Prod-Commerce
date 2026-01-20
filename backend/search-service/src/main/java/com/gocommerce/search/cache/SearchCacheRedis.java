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

    private String buildKey(SearchRequest request) {
        String q = request.query() != null ? request.query().trim().toLowerCase() : "";
        String category = request.category() != null ? request.category().trim().toLowerCase() : "";
        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 20;

        return String.format("search:q=%s|cat=%s|p=%d|s=%d", q, category, page, size);
    }
}
