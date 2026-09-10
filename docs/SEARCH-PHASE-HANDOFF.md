# Search phase handoff — updated 2026-09-09

Read `docs/RELIABILITY-HANDOFF.md` first for the overall plan, the completed checkout phase,
and its limits. Preserve preexisting `docs/interview-prep/`. Nothing described here has
been deployed.

## Status

Phase 2 (independent graded search evaluation and retrieval ablations) is **implemented, and
three graded runs have been collected and scored with AI labels**. Every number is an
`ai_judged_pooled_evaluation` over a generated catalog and AI-authored queries — describe it
that way and never as human relevance judgments. Evidence:
`docs/evidence/search-evaluation-2026-09-09.json` (harness),
`search-evaluation-ai-judged-2026-09-09.json` (first graded run, embedding stub), and
`search-collapse-and-embeddings-2026-09-09.json` (real model, collapse A/B).

Verified locally, all passing (2026-09-09, `mvn -f backend/pom.xml test` with
`SEARCH_TEST_ES_ADDRESS` pointed at a disposable elasticsearch:8.15.2):

| Suite | Command | Result |
| --- | --- | --- |
| Whole backend | `mvn -f backend/pom.xml test` | 309 run, 0 failures, 37 skipped (Docker/PostgreSQL-gated) |
| search-service incl. Elasticsearch contracts | `mvn -f backend/pom.xml -pl search-service -am test` | 78 run, 0 skipped |
| Graded-evaluation harness | `ops/testing/search-evaluation.sh` | 119 tests, 0 skipped |

Two graded runs now exist; see "Result collapsing and real embeddings" below.

The earlier `SearchRetrievalElasticsearchTest` compile failure (ambiguous
`ElasticsearchOperations.search` overload) is fixed by casting the answer's first argument
to `…core.query.Query`. That suite has now run green against a disposable
`elasticsearch:8.15.2` container using a unique fixture index and no project volumes.

## Search service changes

`service/SearchService.java`
- Requested field sorting is preserved in `text`, `vector`, `vector_exact`, and `hybrid`; ties break on
  score, then productId, then slug.
- `vector` uses the indexed cosine HNSW graph with a configurable candidate budget;
  `vector_exact` preserves the old full-scan cosine script as an explicit comparison mode.
- Default `hybrid` stays the lexically gated weighted-cosine baseline (unchanged behavior).
- New experimental `hybrid_rrf`: independent lexical and HNSW-vector queries over shared
  filters, bounded candidate windows, reciprocal rank fusion, deterministic dedup and
  pagination over one stable union. Accepts relevance sort only.
- Unsupported mode/sort and invalid price range return 400. Dependency failure returns 503
  instead of a successful empty result. Zero/non-finite embeddings fail explicitly rather
  than silently switching algorithm, and are never cached.
- Responses carry algorithm/config/total-scope metadata; cached responses whose metadata
  does not match the current configuration are ignored.

`dto/SearchDtos.java` adds `RetrievalInfo` (mode, algorithm, totalRelation, candidateWindow,
rrfRankConstant, keywordWeight, vectorWeight) and an optional `SearchResponse.retrieval`,
keeping the old constructors. `config/SearchProperties.java` adds the ANN candidate budget
(default 100, max 10,000), RRF window (default 100, max 1000), and rank constant (default 60).
`application.yml` wires those and makes the
circuit breaker ignore `ResponseStatusException` — **review whether ignoring every status,
including the 503 from an invalid vector, is what you want.**

`RetrievalInfo` is pure configuration with no per-query counts, which is what lets the
collector require identical retrieval metadata across every query in a run.

## Evaluation harness

`backend/search-service/evaluation/`
- `graded_eval.py` (stdlib only): `validate`, `snapshot`, `collect`, `pool`, `evaluate`,
  `agreement`. Frozen inputs, hashed manifests, exclusive file creation, seeded randomized
  mode order, per-request latency and error records.
- Hardening added this session: numeric catalog validation; `cross_check_items` refuses
  results whose name/slug/price disagree with the frozen snapshot; `load_run` re-derives and
  compares every recorded request parameter against the frozen query; rejected responses are
  retained as `invalid_response` for diagnosis; retrieval-derived fields (`score`, `rank`,
  `embedding`, `highlights`, …) are stripped from reviewer-facing product records.
- `queries.v1.json`: 28 AI-authored, **unjudged** queries, 14 dev / 14 test, seven intent
  groups, authored from tasks rather than sampled catalog rows. Not shopper queries.
- `RUBRIC.md`: `shopper-graded-v1` — 0–3 grades, binary threshold at 2, hard-constraint
  rule (a filter violation must be 0, enforced by the harness), missing-evidence and
  compatibility ceilings, explicit no-match policy, required assessor provenance, and the
  pool-bias / null-NDCG / held-out / small-set / synthetic-corpus limits.
- `README.md`: pipeline commands, an operator config example, the full list of conditions
  the harness refuses to score, and how to read a report.
- `tests/`: 104 tests. `support.py` provides a deterministic in-process stub catalog/search
  server with failure-behavior flags; `test_graded_eval.py` covers metric math and input
  validation; `test_cli_end_to_end.py` drives snapshot → collect → pool → judge → evaluate
  and agreement, including tamper, transport-failure, and blinding checks. Every fixture
  label is `synthetic_fixture` and the scores they produce are meaningless.

## Next concrete work

1. Decide who judges. Until real judgments exist, the only defensible claims are about the
   harness and the retrieval contracts. If an AI assessor is used, the report's
   `evidence_class` becomes `ai_judged_pooled_evaluation` — describe it that way everywhere.
2. Execute a real run: freeze catalog writes and reindexing, flush the search cache, write
   `runs/config.json`, then `snapshot` → `collect` → `pool`. Do **not** run the existing
   destructive reindex just to produce evaluation data.
3. Grade the `dev` split first, tune nothing on `test`, then report `test` with paired
   family bootstrap intervals.
4. One unreachable guard remains by design: `evaluate` re-checks hard-constraint violations
   against the frozen catalog, but `collect` already rejects such results and `load_run`
   verifies the hashes, so it can only fire on a hand-assembled run. Leave it as
   defense-in-depth.
5. Then move to durable indexing and atomic reindexing (aliases), reproducible load testing,
   security, observability — in that order, per the reliability handoff.

## Result collapsing and real embeddings (added 2026-09-09)

Evidence: `docs/evidence/search-collapse-and-embeddings-2026-09-09.json`. Runs:
`backend/search-service/evaluation/runs/2026-09-09-b-uncollapsed` and `-c-collapsed`, which
differ only in `search.collapse.enabled` against the same corpus, queries, model and
judgments. Reproduce either with `COLLAPSE=false|true ops/evaluation/collect-run.sh <out>`.

**The embedding stub is gone.** `collect-run.sh` runs `backend/embedding-service` with the
checked-out BAAI/bge-small-en-v1.5 weights and refuses to start without them, recording the
`model.safetensors` sha256 in the manifest. Earlier vector and hybrid numbers measured a
bag-of-words hashing stub and are **not** a baseline for anything measured since.

**What the catalog now owns.** `products.product_family` (migration V3) groups a product with
its variants; the entity defaults it to the slug on persist, the API publishes it, and search
indexes it. Search derives it from the slug only for rows written before that column existed,
and that derivation is a documented heuristic.

**What collapsing does and does not buy.** Redundant slots per page went from 6.0–6.9 to 0.0
and distinct products shown from 2.2–4.0 to 4.1–9.8. Mean NDCG@10 fell (0.56→0.50 text,
0.83→0.64 vector, 0.75→0.69 RRF) and precision@10 from 0.23 to 0.05. Part of that is a metric
artifact — six colours of the right product are six independently relevant results, and the
pooled NDCG ideal shrinks with the page — which is exactly why `distinct_families@k`,
`redundant_results@k` and `distinct_relevant_families@k` are now computed from the catalog
grouping and need no judgments. The residual is real: freed slots get filled with weaker
matches. **Collapsing is a defensible default, not a measured win. Do not describe it as
one.** The missing piece is `inner_hits`, so a collapsed row can show its own variants.

**Four defects this exercise found**, each of which passed every test beforehand:
`CatalogSeeder` never copied `productFamily`, making collapsing a silent no-op; the seed
catalog gave families only to variants; the cleanup traps in `collect-run.sh` and
`chaos-drill.sh` aborted halfway under `set -e`, leaking an Elasticsearch container per
aborted run and exiting 143 on success; and a stalled (rather than crashed) Redis made a single
search take 120,035ms — the cache read and the cache write each waiting out Lettuce's
60-second default — which the drill's availability-only check scored as a pass.
`spring.data.redis.timeout` is now 250ms in search-service and the drill asserts a latency
budget with the cache stalled: **524ms with the fix, 120,035ms with the default restored,
budget 5,000ms**, so the check is not vacuous. api-gateway (1s) and cart-service (2s) had
the same unbounded default and are now bounded too — **those two are not drill-verified.**

**Judgments carry forward** with `graded_eval.py carry-forward`, which moves a grade to a new
run only when the queries are byte-identical and the product content a reviewer saw is
unchanged, records the pool it came from, and refuses when a newly published product field is
not declared with `--allow-new-field`. `productFamily` is in `DERIVED`, so reviewers never see
the ranker's grouping.

## Verified old evaluation facts

`scripts/generated-queries.jsonl` holds 10,000 rows with no `semantic_intent` or `typo`
families despite the generator supporting them. `relevance-results.json` stores results
under `summary`/`results`/`failures`. Neither file was changed. Those catalog-derived binary
metrics (P@10 ≈ .9457, NDCG@10 ≈ .9574) remain synthetic regression evidence and must not be
described as shopper relevance.

## Changed by the production-readiness phase

Search-service now has a `SecurityConfig`: `GET /api/v1/search/**` stays public, reindex
requires ROLE_ADMIN or the internal service token, and index-product/delete require the
internal token. Reindex scripts and any tooling that called those endpoints anonymously must
now send credentials. `backend/search-service/src/test/resources/application.yml` supplies
test-only values. A new `search_index_documents` gauge and the `SearchIndexEmpty` alert cover
the failure mode where a destructive reindex leaves the index empty while the service looks
healthy. See [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md).

## Operational cautions

- Reindexing builds a timestamped physical index and atomically swaps the `products` alias;
  inactive-product lifecycle remains a separate concern.
- `vector` and the semantic branch of `hybrid_rrf` use approximate HNSW retrieval.
  `vector_exact` remains available for controlled comparisons, and weighted `hybrid` still
  script-scores every document that passes its lexical gate. ANN/RRF totals describe bounded
  candidates, not an exact full-corpus match count.
- Inferred category filters remain active in all retrieval modes and can constrain semantic
  recall — worth an ablation once judgments exist.
- Checkout evidence is unchanged: 24 PostgreSQL, 57 backend unit/API, 10 frontend tests.
- Docker is running; do not stop unrelated containers. Testcontainers 1.20.4 fails Docker 29
  API negotiation locally, which is why the shell runners in `ops/testing/` are used instead.
- Runtime skew: shell Java 25, Maven/Surefire Java 26.0.2.1, Node 25.3.0.
- This phase normalized line endings in the files it touched; restore unchanged CRLF/mixed
  endings if practical (`git -c core.whitespace=cr-at-eol diff --check`). Checkout work
  preserved its original endings.
- Payments remain an explicit mock. Live Kafka replay, process-kill testing, and browser
  verification are still outstanding from phase 1.
