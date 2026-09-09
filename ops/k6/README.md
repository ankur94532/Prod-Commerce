# Load tests

The old benchmark cycled five hard-coded queries across every virtual user, so after a few
seconds almost every request was a cache hit: it measured Redis, not search. These runs
declare their cache regime and draw from a large seeded query set.

## Build a query set

```bash
python3 ops/k6/build-query-set.py --out ops/k6/query-set.json --count 2000 --seed 42
```

Deterministic for a given seed, and it records the corpus checksum so two runs can be
compared. The corpus is catalog-derived synthetic queries — **not shopper traffic**.

## Run

```bash
# Cold: every request a query no virtual user has issued in this run. The pessimistic bound.
BASE_URL=http://localhost:8080 CACHE_REGIME=cold TARGET_RPS=200 BENCHMARK_DURATION=5m \
SUMMARY_OUT=ops/k6/results/cold.json k6 run ops/k6/search-load.js

# Warm: a tiny repeating set, served from cache. The optimistic bound; label it as one.
CACHE_REGIME=warm ... k6 run ops/k6/search-load.js

# Mixed: a popular head plus a long tail, closest to real traffic shape.
CACHE_REGIME=mixed MIXED_HEAD_SIZE=20 MIXED_HEAD_SHARE=0.7 ... k6 run ops/k6/search-load.js

# One retrieval mode at a time, to compare their cost.
SEARCH_MODE=hybrid_rrf ... k6 run ops/k6/search-load.js
```

The scenario uses a **constant arrival rate**, not fixed virtual users. A fixed-VU test
slows its own request rate when the system slows down, which hides the degradation it is
meant to reveal.

## Reading the summary

Each run writes JSON containing the parameters, the query-set provenance, and:

- `failed_rate` — k6 counts a request that never completed, so timeouts and refused
  connections are included. This is what the older `hey`-based script got wrong.
- `distinct_query_requests` / `repeat_query_requests` — evidence of which regime actually ran.
- `zero_result_responses` — a fast run that returns nothing is what an empty index looks
  like. Counting it stops that passing for success.

## What these numbers are not

They describe this environment, this data volume, and a synthetic query mix. They are not a
capacity model, and a warm-regime figure is an upper bound. No number here has been measured
against production traffic, because there is none.

## Verifying the harness

`ops/testing/load-harness.sh` runs the harness against a stub server and checks query-set
determinism, both regimes, transport-error accounting, and the summary shape. It needs no
running stack, and it measures the harness rather than the application.
