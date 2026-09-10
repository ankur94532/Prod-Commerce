package com.gocommerce.search.support;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.AggregationsContainer;

import java.util.Map;

/**
 * Stands in for the aggregation Elasticsearch returns beside a collapsed result page.
 *
 * Collapsed hits are drawn from the same matching set, so the hit total still counts
 * documents; the service asks for a cardinality aggregation on the collapse field to learn
 * how many product families are actually behind the page, and refuses to answer without it
 * rather than reporting a total that promises pages it cannot fill. A mocked
 * ElasticsearchOperations returns no aggregations at all, so tests that stub search results
 * have to supply this or they exercise the refusal instead of the behaviour they name.
 */
public final class StubSearchHits {

    /** The aggregation name the service reads; kept in sync deliberately, not by import. */
    public static final String GROUP_COUNT_AGGREGATION = "collapsed_group_count";

    private StubSearchHits() {
    }

    public static AggregationsContainer<?> groupCount(long groups) {
        return new ElasticsearchAggregations(Map.of(
                GROUP_COUNT_AGGREGATION, Aggregate.of(a -> a.cardinality(c -> c.value(groups)))));
    }
}
