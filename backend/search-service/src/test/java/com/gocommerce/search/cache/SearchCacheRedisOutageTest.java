package com.gocommerce.search.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A chaos drill found search returning errors while only Redis was down. The cache is an
 * optimization: an outage there must cost latency, never availability.
 */
class SearchCacheRedisOutageTest {

    private final SearchRequest request = new SearchRequest("headphones", null, "text", 0, 20);
    private final SearchResponse response = new SearchResponse(List.of(), 0);

    private SearchCacheRedis cacheWith(StringRedisTemplate template) {
        return new SearchCacheRedis(template, new ObjectMapper(), 60);
    }

    // mock() erases the type arguments of ValueOperations, so the assignment below is
    // unchecked by construction rather than by mistake.
    @SuppressWarnings("unchecked")
    @Test
    void anUnreachableCacheReadsAsAMissRatherThanThrowing() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(cacheWith(template).get(request)).isEmpty();
    }

    // mock() erases the type arguments of ValueOperations, so the assignment below is
    // unchecked by construction rather than by mistake.
    @SuppressWarnings("unchecked")
    @Test
    void aSlowCacheReadAlsoDegradesToAMiss() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("timed out"));

        assertThat(cacheWith(template).get(request)).isEmpty();
    }

    // mock() erases the type arguments of ValueOperations, so the assignment below is
    // unchecked by construction rather than by mistake.
    @SuppressWarnings("unchecked")
    @Test
    void anUnreachableCacheSwallowsTheWriteInsteadOfFailingTheSearch() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        doThrow(new RedisConnectionFailureException("connection refused"))
                .when(values).set(anyString(), anyString(), anyLong(), any());

        assertThatCode(() -> cacheWith(template).put(request, response)).doesNotThrowAnyException();
    }

    @Test
    void anUnreachableCacheDoesNotFailAReindex() {
        // clear() runs at the end of a rebuild; stale entries expire on their own.
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.keys(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatCode(() -> cacheWith(template).clear()).doesNotThrowAnyException();
    }
}
