package com.gocommerce.search.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An empty index answers every query with zero results while the service stays up, fast,
 * and healthy by every other signal. Reindex deletes and recreates the live index, so this
 * is precisely what a half-failed reindex looks like from the outside.
 *
 * A negative reading means the count could not be taken; it is never reported as zero,
 * because zero is the alerting condition.
 */
@Component
public class SearchIndexHealthMetrics {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexHealthMetrics.class);
    private static final long UNKNOWN = -1L;

    private final ElasticsearchOperations elasticsearchOperations;
    private final AtomicLong documents = new AtomicLong(UNKNOWN);

    public SearchIndexHealthMetrics(ElasticsearchOperations elasticsearchOperations, MeterRegistry registry) {
        this.elasticsearchOperations = elasticsearchOperations;
        Gauge.builder("search_index_documents", documents, AtomicLong::doubleValue)
                .description("Documents in the product search index; -1 when the count could not be taken")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${search.metrics.index-sample-delay-ms:30000}")
    public void sample() {
        try {
            documents.set(elasticsearchOperations.count(
                    Query.findAll(), com.gocommerce.search.model.ProductDocument.class));
        } catch (RuntimeException error) {
            documents.set(UNKNOWN);
            log.warn("Could not count search index documents: {}", error.toString());
        }
    }
}
