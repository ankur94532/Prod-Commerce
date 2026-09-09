# Reliability implementation and handoff

Updated: 2026-09-09 (local; evidence recorded 2026-09-08 UTC). Local work only; no deployment, publication, or resume edits.
Preexisting untracked `docs/interview-prep/` is user work and must be preserved.
No applicable AGENTS.md found in the repository or ancestor directories.

## Prioritized plan and acceptance criteria

1. **Checkout reliability (implemented locally).** Persist frontend attempt keys across retries/reloads without storing payment details; distinguish paid orders from cart cleanup failures. Concurrent requests with the same user/key produce one order and one outbox event; changed cart payload returns 409. Commit order intent before remote stock changes. Catalog reserve/release operations are atomic and idempotent, including release-before-reserve and lost responses. Persist compensation progress and retry after restart. Deduplicate order-created events atomically with analytics/recommendation updates. Verify concurrency, partial failure, lost responses, rollback, and recovery against PostgreSQL, plus frontend retry tests.
2. **Independent graded evaluation.** Version an independently authored query set, rubric, provenance, and genuine judgments; report agreement and uncertainty. Keep existing catalog-derived binary labels explicitly synthetic. Acceptance: runnable evaluation with held-out judgments and per-intent results; no invented human labels.
3. **Retrieval ablations.** Compare lexical, vector, and hybrid retrieval on identical filters, candidate budgets, and judgments. Acceptance: reproducible runs, sort/filter contract tests, semantic and typo coverage, and measured latency/relevance tradeoffs.
4. **Durable indexing.** Persist catalog change delivery; exclude inactive products; build a versioned index and switch an alias atomically. Acceptance: retries converge after failures, failed reindex leaves the active index intact, restart does not overwrite catalog/inventory.
5. **Reproducible load evidence.** Version workload/seed/configuration, distinguish cold/warm caches and query diversity, account for transport errors and full-run request counts. Inspect installed hey version/sample cap before interpreting reports. Acceptance: raw outputs and parser tests reconcile full-run totals without confusing capped samples with all requests.
6. **Security and observability.** Review search mutation authorization, token purpose separation, secret/header logging, recovery backlog and alerts. Acceptance: denial/validation tests and actionable failure signals. No stack additions without a concrete need.

## Verified architecture and design

- Eight Java modules; inventory is owned by catalog-service, not a separate service.
- Existing checkout holds one order transaction across nontransactional catalog HTTP calls; inventory retries can repeat mutations; compensation exceptions were discarded.
- Orders and outbox commit together. Kafka publication can duplicate. Both consumers previously updated aggregates without deduplication.
- Use the existing PostgreSQL databases: a committed order intent, per-order processing lock, catalog reservation ledger, and transactional consumer inboxes. No new service or broker.
- Recovery cancels abandoned unpaid intents and retries releases using stable order-line reservation IDs. A release tombstone prevents a delayed reserve from decrementing after cancellation.
- Payment remains a mock. Recovery is not sufficient for real payment settlement: a real provider requires durable provider idempotency, status reconciliation, and refund handling before use.

## Implemented increments

1. Catalog `V2__inventory_reservations.sql` and `InventoryService`: reservation ledger plus stock update in one transaction; mandatory `Idempotency-Key` on internal decrement/increment. Stable `order:<id>:line:<id>` identities. Release-before-reserve leaves a permanent tombstone; changed quantity/product conflicts; inactive products cannot be reserved.
2. Order `V4__checkout_recovery.sql`, `OrderIntentService`, `OrderWorkflow`, `CheckoutRecovery`: commit intent before remote mutations; PostgreSQL advisory lock serializes initial user/key claims; order row lock serializes processing/recovery. Only the original creator starts payment. Replays observe persisted state. All planned lines are released on failure, including ambiguous reserve responses. Recovery uses `SKIP LOCKED`, persisted attempts and exponential backoff capped at 300 seconds. Default abandoned-intent threshold is 120 seconds. Two scheduler threads prevent recovery waits from monopolizing outbox publication.
3. Consumer `OrderEventProjector` and `V2__order_event_inbox.sql` in analytics/recommendation: inbox insertion and atomic aggregate upsert share a transaction. Identity is order ID scoped to `order.created` (one logical event per order). Stable product update order avoids multi-line deadlocks. Kafka auto-commit is disabled, record acknowledgement follows processing, and projection failures retry instead of being discarded. Existing aggregate metrics now update after commit.
4. Frontend `src/checkout/attempt.js` and checkout/orders pages: localStorage attempt identity scoped to user, Web Locks for same-origin tab coordination, synchronous duplicate-click guard, no stored payment details. A paid receipt survives cart cleanup failures and reloads. Status lookup works without payment data; explicit new checkout only after PAID/CANCELLED, with stale-tab guards. Nested item validation and redacted payment DTO logging added.

## Contract and recovery notes

- POST `/api/v1/orders`: PAID returns 201; PENDING_PAYMENT/COMPENSATING returns 202; CANCELLED returns 200 with the stored order. A decline now returns an order state rather than the former 402-only error. Consumers of this API must inspect `status`.
- GET `/api/v1/orders/attempt` with `Idempotency-Key` is authenticated and scoped to the current user; unknown/other-user keys return 404.
- Idempotency compares product IDs and quantities; display names/prices and payment credentials are excluded. A key means one purchase attempt, not a chance to charge a different card on replay. Legacy hashes are supported; legacy keys with no hash fail closed.
- Keyless API callers remain compatible but cannot safely retry after a lost response. The frontend always sends a key. Browser storage loss, different browsers/devices, or deliberate new keys are outside that guarantee.
- Existing orders receive workflow version 0 and are **not automatically compensated**: old stock mutations have no reservation ledger identities. New orders use version 1. Legacy pending orders need manual reconciliation, not guessed inventory credits.
- The internal inventory protocol changed: catalog and order code/migrations need a coordinated upgrade before use. No upgrade or deployment was performed here.
- Keep reservation tombstones and inbox identities. Deleting them permits delayed requests/events to apply again. Existing analytics/recommendation totals cannot be safely backfilled into inboxes because their order identities were not stored; a future rollout needs an explicit offset/baseline cutover or a rebuild from complete retained history.
- Inspect recovery with `SELECT id, status, recovery_attempts, next_recovery_at, updated_at FROM orders WHERE status IN ('PENDING_PAYMENT', 'COMPENSATING') ORDER BY updated_at;`. Release failures log order/line IDs, attempt count, and exception class. Alerts/dashboards are still pending.

## Reproduction and evidence

- Baseline: **61 executed tests passed; 1 PostgreSQL test skipped**. (An earlier progress message incorrectly said 71.)
- `mvn -f backend/pom.xml -pl catalog-service,order-service,analytics-service,recommendation-service test -q`: **57 unit/API tests passed**. Dedicated reliability tests skip without the runner's DB environment; the preexisting Testcontainers test also skips because Testcontainers 1.20.4 receives a Docker 29 API 400 response on this machine.
- `ops/testing/checkout-reliability.sh -q`: **24 PostgreSQL tests passed, zero failures/errors/skips**, plus **10 frontend tests passed**. Runs a disposable localhost PostgreSQL container with no project volumes/seeders, applies real Flyway migrations, then stops that container. It fails on skipped/stale reports and writes `backend/target/checkout-reliability-evidence.json`.
- Saved evidence: [checkout-reliability-2026-09-08.json](evidence/checkout-reliability-2026-09-08.json), including test cases, git HEAD, and a fingerprint of the relevant working-tree sources. This is correctness evidence, not throughput/relevance evidence.
- Frontend: `npm run build` passed; `npx eslint src/checkout/attempt.js src/checkout/attempt.test.js src/pages/Checkout.jsx src/pages/OrdersPage.jsx src/api/orders.js` passed. A stale Browserslist data notice remains. Tests use Node's built-in runner, adding no test dependency.
- Environment observed: PostgreSQL 16.11 (`postgres:16`), Docker 29.1.3, Maven 3.9.12, Maven/Surefire Java 26.0.2.1 (the shell `java` resolves to 25.0.1; project compiler targets remain 17/21), Node 25.3.0. Verify local versions when reproducing.
- The in-app browser failed during connection (`sandboxPolicy` metadata missing); no visual/browser interaction result is claimed. No application services or live Kafka integration were exercised by the new tests. Order tests inject catalog/payment failures; catalog and consumer SQL is tested separately on PostgreSQL. Abandoned intents and transaction rollback simulate crash boundaries; no OS process-kill/network-partition campaign was run.
- Initial working tree contained only untracked `docs/`. Preexisting interview-prep files were not edited. All source changes remain local and uncommitted. Original source line endings are preserved; use `git -c core.whitespace=cr-at-eol diff --check` for this repository's CRLF files.

## Remaining limits and next session

- Payments are still a mock. If a real payment succeeds before an order commit fails, this cancellation-only recovery is insufficient; provider idempotency, durable payment reconciliation, and refunds are required before substituting a real provider.
- Processing/recovery holds an order DB lock during bounded HTTP calls. That favors correctness for this increment but has connection/throughput costs not measured here. Backlog alerting, actual restart/partition tests, and live Kafka offset-replay tests remain.
- Cart cleanup is still a whole-cart DELETE without a server-side cart revision. The browser guards known changed carts, but simultaneous cart edits on another device can race cleanup. Conditional cart clearing needs a cart version contract in a later increment.
- Web Locks require a supported browser/secure context; checkout fails before submission otherwise. Browser/UI verification remains outstanding due to the tool connection failure.
- Next planned phase: independently authored graded query evaluation and retrieval ablations. Existing catalog-derived binary evaluations remain synthetic regression evidence. No labels, benchmark results, search behavior, or resume claims were changed in this increment. Search/indexing/load/security findings in the user's notes still need direct verification in their phases.

## Search phase status

Phase 2 (graded search evaluation and retrieval ablations) is implemented and verified at
the harness and retrieval-contract level as of 2026-09-09. Retrieval contracts pass against
a real Elasticsearch (10 executions), the search unit/API suite passes (31 tests, 5 skipped
without the ES env var), and the evaluation harness has 104 passing tests.

No graded run has been collected and `evaluation/queries.v1.json` is AI-authored and
unjudged, so **no new relevance number exists**. Existing catalog-derived binary metrics
remain synthetic regression evidence. See [SEARCH-PHASE-HANDOFF.md](SEARCH-PHASE-HANDOFF.md)
for the changes, the rubric, reproduction commands, and the next steps, and
`evidence/search-evaluation-2026-09-09.json` for the recorded test evidence.

Remaining phases, in order: durable indexing and atomic reindexing, reproducible load
testing, security, observability.

## Production readiness phase (2026-09-09)

Tier 1 and Tier 2 of the production-readiness review are complete and verified locally.
See [PRODUCTION-READINESS.md](PRODUCTION-READINESS.md) for what changed, what is proven, and
the ten items that remain honestly unfinished.

Headline: search-service had no security configuration, so the destructive reindex and the
single-document index endpoints were reachable by anyone; the gateway accepted any string
beginning with "Bearer "; a refresh token worked as an access token; and CI silently skipped
every Docker-gated suite. All fixed with tests. Shared security code now lives in
`backend/platform-security`.

Run `ops/testing/verify-all.sh` for the whole picture: twelve steps, all passing.
Nothing was deployed and the running local cluster was not modified.
