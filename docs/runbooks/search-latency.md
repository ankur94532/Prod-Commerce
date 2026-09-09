# Runbook: search latency and index health

**Alerts:** `SearchLatencyBudgetBurning`, `SearchIndexEmpty`

## SearchIndexEmpty is the urgent one

An empty index answers every query with zero results while the service stays up, fast, and
healthy by every other signal. `search_index_documents` reports `-1` when the count could not
be taken, and `0` only when the index is genuinely empty.

Rebuilds are no longer a plausible cause. `products` is an alias over a timestamped index;
a rebuild fills a new index and only moves the alias once the document count agrees with the
catalog's declared total. A failed or short rebuild leaves the previous index serving. So an
empty index now means something else: a manual deletion, an Elasticsearch data loss, or a
first start that never completed its initial build.

```bash
curl -s "$ES/_cat/indices/products?v"
curl -s "$ES/products/_count"
```

If the index is empty or short, rebuild it. Reindex now requires an administrator token or
the internal service token:

```bash
curl -X POST "$GATEWAY/api/v1/search/reindex" -H "Authorization: Bearer $ADMIN_TOKEN"
```

Watch the response: it reports `catalogProducts`, `indexed`, `indexedDocuments`, and a
`consistent` flag. A mismatch means the rebuild did not finish; do not walk away from it.

## Latency

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{service="search-service",uri=~"/api/v1/search.*"}[5m])))
rate(search_cache_hits_total[5m]) / clamp_min(rate(search_requests_total[5m]), 1e-9)
```

Common causes, in the order worth checking:

1. **Cache cold or Redis down.** Hit ratio near zero with normal traffic. Vector modes
   score every matching document with a script, so they are far more expensive uncached.
2. **Embedding service slow.** `hybrid` and `vector` call it on every cache miss. Check its
   own latency; a dependency failure now returns 503 rather than a false empty result.
3. **Elasticsearch under pressure.** Check heap and pending tasks. Vector retrieval is exact
   script scoring, not approximate nearest neighbour, so cost grows with matching documents.

## Rebuilding

```bash
curl -X POST "$GATEWAY/api/v1/search/reindex" -H "Authorization: Bearer $ADMIN_TOKEN"
```

The response reports `catalogProducts`, `indexed`, `indexedDocuments` and a `consistent`
flag. An inconsistent rebuild is refused rather than published: the alias does not move, the
half-built index is deleted, and search keeps serving the previous one. Repeated refusals
mean the catalog is disagreeing with itself — check its pagination totals before retrying.

Which index the alias points at:

```bash
curl -s "$ES/_cat/aliases/products?v"
```

Leftovers from interrupted rebuilds are cleaned up automatically after six hours.
