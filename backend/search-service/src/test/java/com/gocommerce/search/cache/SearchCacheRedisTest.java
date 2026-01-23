package com.gocommerce.search.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gocommerce.search.dto.SearchDtos.SearchRequest;
import com.gocommerce.search.dto.SearchDtos.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SearchCacheRedisTest {

    /** Simple in-memory “Redis” store we can inspect in assertions. */
    private static class Store {
        Map<String, String> data = new HashMap<>();
        String lastKey;
        long lastTtl;
        TimeUnit lastUnit;
    }

    private Store store;
    private ObjectMapper objectMapper;
    private SearchCacheRedis cache;

    @BeforeEach
    void setUp() {
        store = new Store();
        objectMapper = new ObjectMapper();

        // Dynamic proxy implementing ValueOperations<String,String>
        ValueOperations<String, String> valueOps =
                (ValueOperations<String, String>) Proxy.newProxyInstance(
                        ValueOperations.class.getClassLoader(),
                        new Class[]{ValueOperations.class},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                String name = method.getName();
                                if ("set".equals(name)) {
                                    // We only care about set(key, value, timeout, unit) and set(key, value)
                                    if (args.length == 4) {
                                        String key = (String) args[0];
                                        String value = (String) args[1];
                                        Long timeout = (Long) args[2];
                                        TimeUnit unit = (TimeUnit) args[3];
                                        store.data.put(key, value);
                                        store.lastKey = key;
                                        store.lastTtl = timeout;
                                        store.lastUnit = unit;
                                        return null;
                                    } else if (args.length == 2) {
                                        String key = (String) args[0];
                                        String value = (String) args[1];
                                        store.data.put(key, value);
                                        return null;
                                    }
                                } else if ("get".equals(name)) {
                                    String key = (String) args[0];
                                    return store.data.get(key);
                                } else if ("getOperations".equals(name)) {
                                    // not needed in our tests
                                    return null;
                                }

                                // Everything else is not used by SearchCacheRedis
                                throw new UnsupportedOperationException("Method not supported in test proxy: " + name);
                            }
                        }
                );

        // Minimal StringRedisTemplate that returns our proxy
        StringRedisTemplate template = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOps;
            }
        };

        cache = new SearchCacheRedis(template, objectMapper, 60);
    }

    @Test
    void put_serializesResponseAndStoresWithTtl() {
        SearchRequest req = new SearchRequest("phone", "smartphones", 0, 20);
        SearchResponse resp = new SearchResponse(List.of(), 0);

        cache.put(req, resp);

        String expectedKey = "search:q=phone|cat=smartphones|p=0|s=20";
        assertEquals(expectedKey, store.lastKey);
        assertEquals(60L, store.lastTtl);
        assertEquals(TimeUnit.SECONDS, store.lastUnit);
        assertNotNull(store.data.get(expectedKey));
    }

    @Test
    void get_returnsEmptyOptional_whenRedisHasNoEntry() {
        SearchRequest req = new SearchRequest("x", null, 0, 20);

        Optional<SearchResponse> result = cache.get(req);

        assertTrue(result.isEmpty());
    }

    @Test
    void get_deserializesJsonWhenPresent() throws Exception {
        SearchRequest req = new SearchRequest("phone", null, 0, 20);
        SearchResponse resp = new SearchResponse(List.of(), 1);

        String json = objectMapper.writeValueAsString(resp);
        // buildKey("phone", null, 0, 20)
        String key = "search:q=phone|cat=|p=0|s=20";
        store.data.put(key, json);

        Optional<SearchResponse> result = cache.get(req);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().total());
    }
}
