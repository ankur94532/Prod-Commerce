# Graded search evaluation

A frozen-run, pooled, held-out evaluation harness for the four retrieval modes
(`text`, `vector`, `hybrid`, `hybrid_rrf`). It exists to make an honest comparison
possible; it does not itself produce relevance evidence.

## What this is not

- **Not human judgments.** `queries.v1.json` is AI-authored and currently **unjudged**.
  Its `provenance.author_type` says so, and every report repeats it.
- **Not the old catalog-derived metrics.** `scripts/relevance_eval.py` and
  `scripts/search-engineering-results.json` derive binary labels from the catalog rows that
  generated the queries. Those are synthetic regression numbers (P@10 ≈ .9457,
  NDCG@10 ≈ .9574). They are useful for catching a retrieval regression and are not
  evidence of shopper relevance. This harness does not read or replace them.
- **Not a benchmark of a real assortment.** The 100K-product catalog is generated.
- **Not a latency benchmark.** `elapsed_ms` is a single-pass serial observation recorded
  next to each request for context; the manifest labels it as such.

## Pipeline

Each stage writes files exclusively — it refuses to overwrite existing evidence.

```bash
# 0. Validate the query set (no services needed).
python3 evaluation/graded_eval.py validate --queries evaluation/queries.v1.json

# 1. Freeze the corpus. Stop catalog writes and reindexing first: the snapshot is two
#    equal paginated reads, which detects concurrent change but is not a DB snapshot.
python3 evaluation/graded_eval.py snapshot \
  --base-url http://localhost:8082 --out runs/catalog.jsonl \
  --provenance synthetic_generated_catalog

# 2. Collect all query x mode responses in a seeded randomized order.
python3 evaluation/graded_eval.py collect \
  --base-url http://localhost:8084 --queries evaluation/queries.v1.json \
  --catalog runs/catalog.jsonl --config runs/config.json --out runs/2026-09-09 --depth 20

# 3. Export a blinded pool and a blank judgment template.
python3 evaluation/graded_eval.py pool --run runs/2026-09-09 --out runs/2026-09-09/pool

# 4. Grade every row of judgments.template.jsonl per RUBRIC.md, then:
python3 evaluation/graded_eval.py evaluate \
  --run runs/2026-09-09 --judgments runs/2026-09-09/judgments.jsonl \
  --assessor <assessor-id> --split test --k 10 --out runs/2026-09-09/report.test.json

# Optional, with two assessors over the same pool:
python3 evaluation/graded_eval.py agreement \
  --run runs/2026-09-09 --judgments runs/2026-09-09/judgments.jsonl \
  --first <a> --second <b> --out runs/2026-09-09/agreement.json
```

`runs/config.json` is operator-declared and recorded verbatim in the manifest. The
collector cannot verify any of it, and the manifest says
`index_and_model_identity: operator_declared_not_verified_by_collector`:

```json
{
  "index_identity": "products@2026-09-09T18:04Z, 100000 docs, es 8.15.2",
  "embedding_model": "sentence-transformers/all-MiniLM-L6-v2",
  "embedding_revision": "unknown_unverified",
  "cache_regime": "search cache flushed before run; each query issued once per mode",
  "corpus_provenance": "synthetic_generated_catalog"
}
```

## What the harness refuses to do

It fails loudly rather than producing a number, when: a request failed (errors are never
scored as zero hits), the server omitted or changed retrieval metadata mid-run, a result is
absent from or disagrees with the frozen catalog, a result violates an explicit filter, a
frozen file or recorded request parameter was altered after collection, the run is missing
any query/mode cell, a judgment is blank or out of the 0–3 range, a judgment lacks assessor
provenance or a rationale, a pair falls outside the frozen pool, a pair is judged twice by
one assessor without adjudication, or the pool is incomplete for the split being scored.

## Interpreting a report

- `evidence_class` states who judged: `human_`, `ai_`, or `synthetic_fixture_judged_pooled_evaluation`.
  Quote it whenever you quote a number.
- `by_mode` and `by_kind` are macro means over queries, with `ndcg_scorable_queries` and
  `zero_result_queries` alongside.
- `paired_ndcg_differences` compares each mode to the `text` baseline with a
  query-family bootstrap (2000 resamples). Report `mean_delta` with `ci95`, or not at all.
- `definitions` restates the gain function, the relevance threshold, the fixed precision
  denominator, and the pool-bounded meaning of recall.

## Tests

`ops/testing/search-evaluation.sh` runs the harness test suite (104 tests: metric math,
input validation, response contracts, judgment loading, and a full CLI pipeline against an
in-process stub server). The stub's judgments are labeled `synthetic_fixture`; the scores
they produce are meaningless and exist only to prove the pipeline behaves. Retrieval
contracts themselves are covered separately by `ops/testing/search-retrieval.sh`.

## Status

No graded run has been executed. `queries.v1.json` has no judgments. Until a judged run
exists, the only defensible claims are about the harness and the retrieval contracts.
