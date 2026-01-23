package com.gocommerce.search.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class SearchMetrics {

    private final Counter searchRequests;
    private final Counter searchCacheHits;
    private final Counter searchCacheMisses;
    private final Counter zeroResultSearches;
    private final Timer searchTimer;
    private final Counter reindexRuns;
    private final DistributionSummary reindexProducts;

    public SearchMetrics(MeterRegistry registry) {
        this.searchRequests = Counter.builder("search_requests_total")
                .description("Total search requests received")
                .tag("service", "search-service")
                .register(registry);

        this.searchCacheHits = Counter.builder("search_cache_hits_total")
                .description("Search requests served from cache")
                .tag("service", "search-service")
                .register(registry);

        this.searchCacheMisses = Counter.builder("search_cache_misses_total")
                .description("Search requests that missed cache")
                .tag("service", "search-service")
                .register(registry);

        this.zeroResultSearches = Counter.builder("search_zero_results_total")
                .description("Searches that returned zero results")
                .tag("service", "search-service")
                .register(registry);

        this.searchTimer = Timer.builder("search_request_duration_seconds")
                .description("Time spent executing search queries")
                .tag("service", "search-service")
                .register(registry);

        this.reindexRuns = Counter.builder("search_reindex_runs_total")
                .description("How many times full reindex was triggered")
                .tag("service", "search-service")
                .register(registry);

        this.reindexProducts = DistributionSummary.builder("search_reindex_products_total")
                .description("Number of products indexed per reindex run")
                .baseUnit("products")
                .tag("service", "search-service")
                .register(registry);
    }

    public void recordCachedSearch() {
        searchRequests.increment();
        searchCacheHits.increment();
    }

    public void incrementCacheMiss() {
        searchCacheMisses.increment();
    }

    public void incrementZeroResult() {
        zeroResultSearches.increment();
    }

    public <T> T timeSearch(Supplier<T> supplier) {
        searchRequests.increment();
        return searchTimer.record(supplier);
    }

    public void onReindexCompleted(int productCount) {
        reindexRuns.increment();
        reindexProducts.record(productCount);
    }
}
