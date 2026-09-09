# Search phase handoff — updated 2026-09-09

Read `docs/RELIABILITY-HANDOFF.md` first for the overall plan, the completed checkout phase,
and its limits. All work remains uncommitted. Preserve preexisting `docs/interview-prep/`
and all checkout changes. No AGENTS.md was found; recheck. No deployment, publishing, or
resume rewrite is authorized.

## Status

Phase 2 (independent graded search evaluation and retrieval ablations) is **implemented and
verified at the harness/contract level**. No graded run has been collected and no relevance
number has been produced. Evidence: `docs/evidence/search-evaluation-2026-09-09.json`.

Verified locally, all passing:

| Suite | Command | Result |
| --- | --- | --- |
| Elasticsearch retrieval contracts | `ops/testing/search-retrieval.sh` | 10 executions, 0 skipped |
| search-service unit and API | `mvn -f backend/pom.xml -pl search-service test` | 31 tests (26 run + 5 ES cases skipped without the env var) |
| Graded-evaluation harness | `ops/testing/search-evaluation.sh` | 104 tests, 0 skipped |

The earlier `SearchRetrievalElasticsearchTest` compile failure (ambiguous
`ElasticsearchOperations.search` overload) is fixed by casting the answer's first argument
to `…core.query.Query`. That suite has now run green against a disposable
`elasticsearch:8.15.2` container using a unique fixture index and no project volumes.

## Search service changes

`service/SearchService.java`
- Requested field sorting is preserved in `text`, `vector`, and `hybrid`; ties break on
  score, then productId, then slug.
- Default `hybrid` stays the lexically gated weighted-cosine baseline (unchanged behavior).
- New experimental `hybrid_rrf`: independent lexical and exact-vector queries over shared
  filters, bounded candidate windows, reciprocal rank fusion, deterministic dedup and
  pagination over one stable union. Accepts relevance sort only.
- Unsupported mode/sort and invalid price range return 400. Dependency failure returns 503
  instead of a successful empty result. Zero/non-finite embeddings fail explicitly rather
  than silently switching algorithm, and are never cached.
- Responses carry algorithm/config/total-scope metadata; cached responses whose metadata
  does not match the current configuration are ignored.

`dto/SearchDtos.java` adds `RetrievalInfo` (mode, algorithm, totalRelation, candidateWindow,
rrfRankConstant, keywordWeight, vectorWeight) and an optional `SearchResponse.retrieval`,
keeping the old constructors. `config/SearchProperties.java` adds the RRF window (default
100, max 1000) and rank constant (default 60). `application.yml` wires those and makes the
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

- Reindex still deletes and recreates the active index. Durable indexing and atomic aliases
  are later work; inactive products can still remain searchable.
- Vector retrieval is exact script scoring, not ANN. The RRF candidate window bounds
  candidates per branch, not documents scanned; its `total` is candidate-union size, not an
  exact full-corpus match count.
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
