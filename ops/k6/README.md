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

For the warm regime, preload the five repeated keys before starting a high-rate run. Starting
Redis empty and launching hundreds of VUs at once creates a cache stampede; calling that
result "warm" is incorrect. The application benchmark evidence records the exact preloading
procedure it used.

## Shopper journey

`shopper-journey.js` adds cross-service reads and writes. Its distribution is deliberately
declared as invented: 50% browse only, 30% browse then search, and 20% browse → search → add
to cart → checkout. It measures saturation and write contention, not real shopper behavior.

Cart and order endpoints require a real RS256 access token. Create a disposable key and mint
the token with the same helper used by the chaos drills:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/load-key.pem
ACCESS_TOKEN="$(python3 ops/testing/mint-access-token.py \
  /tmp/load-key.pem load-key load-shopper-1 USER 3600)"

BASE_URL=http://localhost:8080 ACCESS_TOKEN="$ACCESS_TOKEN" USER_ID=load-shopper-1 \
TARGET_RPS=5 BENCHMARK_DURATION=2m \
SUMMARY_OUT=ops/k6/results/shopper-journey.json \
k6 run ops/k6/shopper-journey.js
```

Configure the services/gateway with the corresponding public key and key id. The payment
token is handled only by the repository's mock provider; no money moves.

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

## The traffic model

`ops/k6/traffic-model.json` holds the shape of shopper traffic: how far sessions get, the
head/tail split of queries, think time, and whether shoppers share a cart. `shopper-journey.js`
reads it rather than hard-coding a distribution.

**Every number in it is assumed, not observed, and the file says so in its own `status` field.**
It exists so the assumptions can be argued with in one place instead of being buried in control
flow, and so they can be replaced with one edit when the shop has real traffic.

The parameter that matters most is `session_mix.completes_checkout`. It was 20% when the
journey script was first written, against a real e-commerce norm of roughly 1–3%: that
overstated write load by about ten times and would have made checkout look like the dominant
cost of a shopper session. It is 1.2% now. If you quote write capacity from a run, quote this
number alongside it.

```bash
# Default: distinct shoppers, industry-shaped conversion, think time between steps.
TRAFFIC_MODEL=./traffic-model.json ACCESS_TOKEN=$(python3 ../testing/mint-access-token.py \
    /path/to/jwt.pem drill-key load-shopper) \
  k6 run shopper-journey.js

# The pathological case: every virtual user contending on one cart. A different question,
# and not comparable to the run above.
#   set shoppers.mode to "shared" in a copy of the model
```

What this cannot do: surface the queries nobody predicted. That is the main thing real
traffic is worth, and a model of its shape is not a substitute for it.

