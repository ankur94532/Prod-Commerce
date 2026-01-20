package com.gocommerce.search.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchCacheRedisTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private SearchCacheRedis cache;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new SearchCacheRedis(redisTemplate, objectMapper, 60);
    }

    @Test
    void put_serializesResponseAndStoresWithTtl() {
        SearchRequest req = new SearchRequest("phone", "smartphones", 0, 20);
        SearchResponse resp = new SearchResponse(List.of(), 0);

        cache.put(req, resp);

        verify(valueOperations).set(
                eq("search:q=phone|cat=smartphones|p=0|s=20"),
                anyString(),
                eq(60L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void get_returnsEmptyOptional_whenRedisHasNoEntry() {
        SearchRequest req = new SearchRequest("x", null, 0, 20);
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<SearchResponse> result = cache.get(req);

        assertTrue(result.isEmpty());
    }

    @Test
    void get_deserializesJsonWhenPresent() throws Exception {
        SearchRequest req = new SearchRequest("phone", null, 0, 20);
        SearchResponse resp = new SearchResponse(List.of(), 1);

        String json = objectMapper.writeValueAsString(resp);
        when(valueOperations.get(anyString())).thenReturn(json);

        Optional<SearchResponse> result = cache.get(req);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().total());
    }
}
