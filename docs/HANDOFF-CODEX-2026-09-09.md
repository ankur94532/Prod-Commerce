# Handoff — Prod-Commerce, 2026-09-09

## Continuation completed later on 2026-09-09

- Compose frontend started and verified: healthy, `/healthz` 200, deep link 200. Its IPv6
  false-negative healthcheck was fixed. The owner's `.env` was not edited.
- First real local application benchmark recorded, including cold and preloaded-warm knees.
- All five SLI recordings evaluated against real scraped metrics; healthy zero-series bug
  fixed; gateway fallback changed from misleading empty 200 to 503; fast-burn alert observed
  firing after its configured five-minute hold.
- Invented, explicitly labelled browse/search/cart/checkout k6 journey added and harnessed.
- Gateway Redis limiter drill added and passed: deliberate fail-open, slowest 1,051ms.
- Second AI assessor graded all 175 test pairs: exact agreement 84.57%, linear-weighted kappa
  0.7454. No human labels were created.
- Order drill now covers a reachable read-only PostgreSQL: reads worked, checkout failed 500
  without a phantom row, and recovered to 201 without an application restart.

Evidence: `docs/evidence/frontend-compose-2026-09-09.json`,
`load-benchmark-2026-09-09.json`, `slo-machinery-2026-09-09.json`,
`chaos-gateway-2026-09-09.json`, `chaos-order-2026-09-09.json`, and
`search-inter-assessor-agreement-2026-09-09.json`.

You are continuing work on `/Users/shashwattripathi/Downloads/Prod-Commerce`. Read this
whole file before touching anything. Then read `docs/PRODUCTION-READINESS.md` and
`docs/SEARCH-PHASE-HANDOFF.md`.

## Non-negotiable constraints (from the repository owner)

1. **Never fabricate human labels, benchmark results, or production guarantees.** If a
   number cannot be measured, say so and leave it unmeasured.
2. **Do not silently relabel synthetic data as real.** Every evidence file states its own
   limits; keep doing that.
3. **Work locally. Do not deploy, publish, or rewrite the resume.** Kubernetes work is done
   in a self-deleting scratch namespace; the `gocommerce` namespace is never touched.
4. Prefer correctness, failure recovery, honest evaluation and reproducible evidence over
   adding services or features.
5. Keep evidence in `docs/evidence/*.json` and update `docs/PRODUCTION-READINESS.md`.

## Repository state

- Branch `main`, last commit `41a6f33`.
- **69 paths are modified or new and NOTHING IS COMMITTED.** The owner has not yet asked for
  a commit. Ask before committing, or leave it dirty.
- No stray containers, no leftover scratch namespaces. Verify with:
  `docker ps` and `kubectl get ns` before and after any drill.

## Hard-won lessons — read these or you will repeat them

These cost hours this session. All of them produced results that *looked* fine.

1. **You are probably testing a stale artifact.** Four separate times: seven-month-old
   container images, a `target/` jar older than its source, a `docker build -q` whose output
   I misread, and a shell-mangled tag. Always verify the artifact is newer than its source.
   Tools that exist for this: `ops/testing/image-is-current.py`, and the jar-freshness guards
   inside `ops/testing/chaos-drill-cart.sh` and `chaos-drill-order.sh`.
2. **zsh mangles `-t $s:latest`.** `$s:l` is zsh's lowercase parameter modifier, so the tag
   silently becomes `<service>atest` and `<service>:latest` keeps an old build, while
   `docker build` reports success. **Always quote: `-t "${s}:latest"`.**
3. **macOS ships bash 3.2.** No `declare -A`, no `mapfile`. No GNU `timeout`. BSD `find` has
   no `-newermt @epoch`. BSD `stat` prints local time.
4. **`set -o pipefail` + `head -c` closing a pipe = exit 141.** Bound the reader instead.
5. **Cleanup traps must not abort halfway.** Under `set -e`, a false `[ -n "$pid" ]` or a
   `wait` on a just-killed process (returns 143) ends the trap early and leaks containers.
   The pattern used everywhere here: capture `$?`, guard re-entry with a `cleaned` flag,
   `set +e`, never `wait`, `return "$status"`.
6. **A loose assertion is as bad as a stale artifact.** The order drill's baseline accepted
   any 2xx; 202 means the order never completed, so every later assertion examined an order
   that was never placed. Assert the specific status and the specific database state.
7. **Tests can measure the wrong axis entirely.** 41 platform-security tests passed while
   auth-service could not start in any environment-variable deployment, because all 41 bound
   configuration from properties and production binds from the environment.
8. Do not run two drills concurrently: several write to fixed `/tmp` paths and they interleave.

## What was completed this session (do not redo)

Search quality:
- Fuzzy matching moved onto the concatenated `searchText` field. `"rechargable trimer"` went
  from zero results to the beard trimmers.
- Variant collapsing on `productFamily`, owned by the catalog (migration `V3__product_family.sql`,
  `NOT NULL`, defaulted on persist). Search derives it from the slug only as a documented
  fallback for older rows.
- Real embeddings: `BAAI/bge-small-en-v1.5` pinned to revision
  `5c38ec7c405ec4b44b94cc5a9bb96e735b38267a`, baked into the image, sha256-verified at build.
- New evaluator metrics `distinct_families@k`, `redundant_results@k`,
  `distinct_relevant_families@k` — NDCG provably cannot see variant redundancy.
- `graded_eval.py carry-forward` moves judgments between runs only when the queries and the
  reviewed product content are byte-identical.
- Evidence: `docs/evidence/search-collapse-and-embeddings-2026-09-09.json`.
- **Collapsing is a defensible default, NOT a measured win.** It removes redundancy
  completely and costs mean NDCG. Do not describe it as an improvement.

Kubernetes:
- `k8s/data-tier/` — PostgreSQL, Redis, Elasticsearch, Kafka. Previously the ConfigMap
  pointed at hosts nothing provided, so `kubectl apply -k k8s/` started nothing.
- `deploy/overlays/production-managed-data/` deletes those and repoints at managed endpoints.
- `k8s/frontend.yaml` + `frontend/Dockerfile` + ingress routing (`/api` to gateway, rest to
  the SPA) + PDB + HPA + CI image build + overlay pinning.
- `ops/testing/data-tier-deploy.sh` — 7 checks, all ten deployments start, product retrieved
  across the cluster network. Evidence: `docs/evidence/kubernetes-data-tier-2026-09-09.json`.

Chaos:
- `ops/testing/chaos-drill.sh` (search) now asserts a latency budget with a stalled cache:
  524ms with the fix, 120,035ms without.
- `ops/testing/chaos-drill-cart.sh` — 4 checks. Redis as system of record: fails loudly
  (500), never a false empty cart, 2,026ms, recovers intact.
- `ops/testing/chaos-drill-order.sh` — 6 checks. PostgreSQL down: 500 with no phantom order.
  Kafka down: **201, checkout still completes** (the outbox's whole purpose), events held
  unpublished, drained on recovery.
- `ops/testing/mint-access-token.py` mints RS256 tokens with openssl + stdlib.

Defects found in committed code and fixed (all invisible to `kubectl apply --dry-run`):
- Migration Jobs for auth/catalog/order crash-looped: they run the service image so Spring
  builds the whole context, but carried none of the Secrets their Deployments carry.
- `JwtProperties` could not bind from environment variables — `getPrivateKey()` on a
  `@ConfigurationProperties` bean made Spring's binder treat `security.jwt.private-key` as a
  property. auth-service could not start under Kubernetes or Compose. Accessors renamed to
  `resolve*`; regression test `JwtPropertiesEnvironmentBindingTest`.
- Kafka StatefulSet hardcoded the `gocommerce` namespace (would break the staging overlay).
- `embedding-service` had no model on disk with a read-only root, and an unpinned download.
- `CatalogSeeder` silently dropped `productFamily`, making collapsing a no-op.
- Redis command timeouts were unbounded (Lettuce default 60s) in search, gateway and cart.
- `frontend/src/api/apiBase.js` used `||`, which treats `""` as unset — a same-origin
  deployment would have baked `localhost:8080` into the bundle.
- Cleanup traps in `collect-run.sh` and `chaos-drill.sh` leaked containers and exited 143.

## IN FLIGHT — finish this first

`docker-compose.yml` has just gained a `frontend` service (nginx over the built bundle, to
match how the cluster serves it). It validates:

```bash
docker compose config --quiet   # needs the DB password vars set
```

**Unverified:** it has never been started. Run `docker compose up -d frontend` and confirm
`http://localhost:5173/healthz` and a deep link such as `http://localhost:5173/orders/1`
return 200. Then record it.

**Separately found, not yet fixed:** the owner's local `.env` predates the per-service
database roles and lacks `AUTH_DB_PASSWORD`, `CATALOG_DB_PASSWORD`, `ORDER_DB_PASSWORD`,
`ANALYTICS_DB_PASSWORD`, `RECOMMENDATION_DB_PASSWORD`, so `docker compose config` fails.
`.env.example` documents all five correctly. Do not edit `.env` (untracked, the owner's).
Consider adding a check to a drill or documenting it in the README.

## Remaining tasks, in priority order

### 1. Application load benchmark (highest value)

`ops/k6/` has a real harness — constant arrival rate, seeded query sets, declared cold/warm/
mixed cache regimes — that **has only ever run against a stub** (`ops/testing/load-harness.sh`
verifies the harness, not the application). No latency, throughput or saturation number for
this system exists, so the resource requests and HPA thresholds in `k8s/` are guesses.

Do:
- Stand the system up. Easiest proven path: `ops/evaluation/collect-run.sh` shows how to
  bring up pg + es + redis + embedding + catalog + search on random ports. Or reuse the
  scratch-namespace bring-up in `ops/testing/data-tier-deploy.sh`.
- Run k6 (`grafana/k6` image; k6 is not installed on the host) per `ops/k6/README.md` for
  the cold and warm regimes at a few arrival rates until latency degrades.
- Write `docs/evidence/load-benchmark-2026-09-09.json` with: hardware, single-replica
  topology, corpus size and provenance, cache regime, arrival rate, achieved RPS, p50/p95/p99,
  error rate, and where it saturated.
- **State plainly that these numbers do not transfer to production**: one replica per service,
  a 600-product generated catalog, a laptop, and a synthetic query distribution.

### 2. SLO machinery has never been evaluated

`ops/prometheus/rules/gocommerce.rules.yml` defines recording rules and multi-window
burn-rate alerts. `docs/SLO.md` defines the objectives. **None of it has ever been run against
real metrics** — nobody knows whether the PromQL returns anything at all.

Do:
- With the system under load from task 1, run Prometheus (it is in `docker-compose.yml` with
  the rules already mounted) and confirm the recorded series exist and are sane:
  `job:gateway_requests:rate5m`, `job:gateway_errors:rate5m`, `job:gateway_availability:ratio5m`,
  `job:gateway_error_budget_consumed:ratio30d`, `job:search_latency_p95:5m`.
- The rules select on `service="api-gateway"`. That label comes from the application's own
  `management.metrics.tags` (see `backend/api-gateway/src/main/resources/application.yml`
  around line 176). **Verify the label is actually present on scraped metrics** — if it is
  not, every rule silently matches nothing, which is the classic version of this bug.
- Inject errors (pause a dependency) and assert `GatewayErrorBudgetBurningFast` actually
  transitions to firing. That is a pager drill without a pager.
- This is **not** measuring a production SLO and must not be described as one. It is
  verifying that the SLI plumbing works.

### 3. Journey-shaped load, not just search

The k6 scripts only hit `/search`. Nothing exercises browse → search → add to cart →
checkout, which is where cross-service interaction and write contention live.

Do: add `ops/k6/shopper-journey.js` with a weighted journey, using `mint-access-token.py`
for authentication (cart and orders require a real RS256 token; see the chaos drills for the
exact claims). Label the distribution as invented. What it buys is saturation behaviour and
write contention; what it cannot buy is a realistic query mix.

### 4. Gateway rate-limiter chaos

`backend/api-gateway/src/main/resources/application.yml` got `spring.data.redis.timeout: 1s`
this session, and it is the **only one of the three Redis timeouts never drill-verified**
(search: 524ms verified; cart: 2,026ms verified). The gateway's Redis rate limiter runs on
every request, so a stalled Redis there stalls the entire edge.

Do: model it on `ops/testing/chaos-drill-cart.sh`. Start Redis + api-gateway, pause Redis,
and assert the request completes within a budget well under 60s. Decide and document whether
the limiter fails open or closed — either is defensible, but it must be a decision.

### 5. Inter-assessor agreement for the search evaluation

The relevance labels are AI-produced by a single assessor (`claude-opus-5`, declared
`assessor_type: ai`). Human judgments cannot be fabricated — do not try. But
`graded_eval.py agreement` exists and **has never been used**: it computes linear-weighted
Cohen's kappa between two assessors over the same pool.

Do: grade the same pool with a second, independent AI assessor (a different model, or a
deliberately different prompt/persona — record which, and how), then run:

```bash
python3 backend/search-service/evaluation/graded_eval.py agreement \
  --run backend/search-service/evaluation/runs/2026-09-09-c-collapsed \
  --judgments <combined.jsonl> --first claude-opus-5 --second <second-assessor> \
  --out .../agreement.json
```

Report kappa alongside the metrics. It remains an AI-labelled benchmark; agreement gives it
a reliability measure it currently lacks. Do not present agreement as accuracy.

### 6. Extend chaos to subtler faults

Everything so far pauses a container, which is a network black hole. Untested: packet loss,
slow responses, and a database that accepts connections but fails writes (for example a full
disk or a read-only replica). The last one is the most valuable and the most likely to find
something.

## Cannot be closed locally — do not attempt

- **Real production traffic.** Synthetic journeys are worth building (task 3) but the
  distribution is assumed, not observed. Never present the result as production behaviour.
- **Paging.** `k8s/alertmanager-secret.example.yaml` needs a real destination from the owner.
- **NetworkPolicy enforcement.** Verified NOT enforced on docker-desktop; needs a CNI that
  supports it. All 9 policies are currently decoration.
- **Managed data stores, off-site backups, a real ingress hostname, real image tags
  (`sha-REPLACE_ME` in all overlays), an external secret controller, a real payment
  provider.** All need values or infrastructure only the owner can supply.

## How to verify your work

```bash
# Whole backend (needs a disposable Elasticsearch for the gated suites)
SEARCH_TEST_ES_ADDRESS=127.0.0.1:<port> mvn -o -f backend/pom.xml test   # 312 tests, 0 failures

# Evaluation harness
cd backend/search-service/evaluation && python3 -m unittest discover -s tests -t tests  # 119

# Frontend
cd frontend && npm test && npm run lint    # 34 tests

# Drills (run one at a time; each is self-cleaning)
ops/testing/data-tier-deploy.sh     # 7 checks, ~6 min, scratch namespace
ops/testing/chaos-drill.sh          # 5 checks, search
ops/testing/chaos-drill-cart.sh     # 4 checks
ops/testing/chaos-drill-order.sh    # 6 checks
```

Always finish by confirming nothing leaked: `docker ps` and `kubectl get ns`.
