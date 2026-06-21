# k6 Load Tests

Run these from the repository root after the Docker Compose stack and API Gateway are up.

Install k6:

```bash
brew install k6
```

Mixed-query search benchmark with a warmup phase and a 10-minute benchmark phase:

```bash
k6 run ops/k6/search-load.js
```

Production-style search benchmark knobs:

```bash
BASE_URL=http://localhost:8080 \
WARMUP_VUS=10 \
WARMUP_DURATION=5m \
BENCHMARK_VUS=50 \
BENCHMARK_DURATION=10m \
k6 run ops/k6/search-load.js
```

Repeated-query cache benchmark:

```bash
SEARCH_QUERY=iphone VUS=20 DURATION=2m k6 run ops/k6/cache-comparison.js
```

The scripts write JSON summaries into `ops/k6/results/` and print resume-ready latency, throughput and error-rate numbers to stdout.
