# Production readiness — Tier 1 and Tier 2

Verification completed 2026-09-09. **Nothing was deployed**, and the running local cluster
was not modified. Evidence:
[`evidence/production-readiness-2026-09-09.json`](evidence/production-readiness-2026-09-09.json).

Run everything with `ops/testing/verify-all.sh` (seventeen steps, all passing).

| Suite | Command | Result |
| --- | --- | --- |
| Backend unit and API | `mvn -f backend/pom.xml test` | 241 tests, 0 failures, 50 Docker-gated skips (was 160) |
| Checkout reliability | `ops/testing/checkout-reliability.sh` | 36 PostgreSQL + 10 frontend, 0 skips (was 24) |
| Database role isolation | `ops/testing/database-isolation.sh` | 26 checks |
| Database role migration | `ops/testing/database-role-migration.sh` | 23 checks |
| Schema migration job | `ops/testing/migration-job.sh` | 4 lifecycle checks |
| Consumer-driven HTTP contracts | `ops/testing/contracts.sh` | 4 boundaries, both sides |
| Search Elasticsearch contracts | `ops/testing/search-retrieval.sh` | 22 executions |
| Graded evaluation harness | `ops/testing/search-evaluation.sh` | 104 tests |
| Load-test harness | `ops/testing/load-harness.sh` | regimes, accounting, determinism |
| Point-in-time recovery | `ops/backup/pitr-drill.sh` | restore to an instant, verified |
| Frontend | `node --test "src/**/*.test.js"` | 21 tests (was 10), lint clean |
| Config validation | `ops/testing/verify-all.sh` | k8s ×2, Compose, promtool, amtool |

---

## Tier 1

### 1. Search mutation endpoints were unauthenticated

`POST /api/v1/search/reindex` rebuilt the **live** product index in place (it is atomic now;
see item 4 below). `POST /index-product` and `DELETE /products/{id}` insert or remove documents. Search-service
had no `SecurityConfig` at all — it was the only service without one — and the gateway
treated all of `/api/v1/search/**` as public. Any anonymous caller could empty the catalog
from search or inject documents into results.

- New `search-service/config/SecurityConfig`: `GET` stays public; reindex requires
  `ROLE_ADMIN` or the internal service token; the single-document endpoints require the
  internal token only, because catalog owns that path.
- Catalog's `SearchIndexClient` and search's `RecommendationClient` now present
  `X-Internal-Service-Token`; recommendation's `/internal/**` is guarded too.
- **9 authorization tests** (`SearchSecurityRulesTest`) cover anonymous, shopper, admin,
  refresh-token, wrong-token, and correct-token cases, and assert the service method is
  never reached when rejected.

### 2. The gateway did not authenticate

`AuthHeaderFilter` accepted any header starting with `Bearer ` — no signature, no expiry, no
audience — and treated any path *containing* `/health` as public, so
`/api/v1/admin/users/health` skipped the check entirely.

- The filter now verifies signature, expiry, and token type through the shared verifier.
- Public routes are matched by **method and whole path segment**, so `/api/v1/products-admin`
  no longer matches the `/api/v1/products` prefix, and search mutations are not public.
- Client-supplied `X-Internal-Service-Token`, `X-Auth-User-Id`, and `X-Auth-User-Role`
  headers are stripped before forwarding, so a caller cannot claim to be a service.
- **14 tests** including the `/health` bypass, the prefix bypass, and forged tokens.

**Also found while reading:** access and refresh tokens were signed with the same key and
carried no `type` claim, so a refresh token — long-lived, stored less carefully — worked as
an access token on every protected API. There was also no refresh endpoint, so the refresh
token was pure liability. Both fixed: tokens now carry `type`, verification requires the
expected type, and `POST /api/v1/auth/refresh` exchanges one for a new pair. Access tokens
dropped from 60 minutes to 15, with the frontend refreshing on 401 (single-flight, so
concurrent expiries make one refresh call).

### 3. Actuator was public

`/actuator` was on the gateway's public list, and the ingress forwards `/` to the gateway,
so `/actuator/prometheus` was internet-reachable. Actuator now binds to its own port (9090)
in deployed environments, probes and scraping follow it, the gateway no longer treats it as
public, and a NetworkPolicy limits it to the monitoring namespace.

### 4. CI did not run the tests that matter

`ci.yml` ran `mvn test`, where the checkout PostgreSQL suite, the Elasticsearch retrieval
contracts, and the evaluation harness all **silently skipped** for want of Docker. Separately,
`docker-images.yml` had no `needs:`, packaged with `-DskipTests`, and published images on
every push regardless of results.

CI now runs five parallel jobs covering every suite above, and the image workflow calls it as
a required reusable workflow, so nothing publishes unless the tests pass.

---

## Tier 2

### 5 and 7. Database isolation, and stateful infrastructure

Every service connected as the same role, so a flaw in any one reached every other's data:
analytics could read password hashes. Each service now owns exactly one database with a
least-privilege role, `PUBLIC` connect is revoked, and Kubernetes injects a per-service
credential. `ops/testing/database-isolation.sh` proves it: **26 checks**, each role reaching
its own database and denied all four others.

`ops/backup/pg-backup.sh` dumps every database with checksums and table counts;
`ops/backup/pg-restore-drill.sh` restores into a throwaway server and compares against the
manifest. Both were run end to end.

Point-in-time recovery was added afterwards (see item 7 below), so recovery is no longer
limited to the last dump.

**Still true, and stated plainly:** this is one PostgreSQL instance — one failure domain,
one backup, one upgrade window. The per-service URLs already differ, so splitting is a
configuration change. There is still no backup schedule, no offsite copy, and no archive
retention, and therefore no honest RPO or RTO.
See [`runbooks/backup-restore.md`](runbooks/backup-restore.md).

### 6. Event durability

Producer durability is now declared rather than inherited from client defaults (`acks=all`,
idempotence, bounded delivery timeout). Consumers read `read_committed`.

The consumer error handler previously retried a poison record **forever**, which kept data
correct but blocked its partition indefinitely and silently. It now retries with exponential
backoff and parks the record on `<topic>.DLT`, with an alert on failures. Inbox
deduplication makes replay safe. A parked event is not a processed event — that is what the
alert and [`runbooks/event-dead-letters.md`](runbooks/event-dead-letters.md) are for.

Compose moved from ZooKeeper to **KRaft**; verified by starting a broker from the new
settings (ready in 3s, topic created, quorum healthy). It is still a single broker at
replication factor 1: fine for local development, not a production topology.

### 8. Container images

Dockerfiles copied a jar that had to already exist on the build host, so images could not be
rebuilt from the repository and ran as root. All nine now build from source in a multi-stage
build, extract Spring Boot layers for cache-friendly ordering, run as uid 10001, and set
container-aware JVM flags with `ExitOnOutOfMemoryError`. Verified by building auth-service
and booting it.

### 9. Kubernetes manifests

Added: non-root `securityContext` with `readOnlyRootFilesystem` and all capabilities dropped,
per-service ServiceAccounts with no mounted token, PodDisruptionBudgets, HPAs for the
request-serving path, topology spread, NetworkPolicies (default-deny plus explicit allows),
and TLS with forced redirect and a rate limit on the ingress.

`:latest` remains in the base for local use; `deploy/overlays/production` pins immutable
`sha-` tags. Both render and validate.

### 10. Nobody was being paged

Prometheus scraped nine services with no `rule_files` and no Alertmanager; Grafana had
datasources and zero dashboards. Added 15 validated alert rules, Alertmanager routing with
severity-based paths, a 10-panel dashboard, [`SLO.md`](SLO.md), and **seven runbooks** — every
alert names one.

Two alerts had no metric behind them, so the metrics were built: `order_recovery_backlog`,
`order_recovery_oldest_seconds`, `order_outbox_pending`, `order_outbox_oldest_seconds`
(6 tests against real PostgreSQL, including that a failed sample keeps the last reading
rather than reporting a false zero), and `search_index_documents`, which reports `-1` rather
than `0` when the count fails — because `0` is the alerting condition.

### 11. Copy-pasted security code

`JwtService`, `JwtProperties`, and the auth filters existed in five services, so a security
fix had to land five times. All of it now lives in the `platform-security` module
(**29 tests**), consumed by all eight services and the gateway.

### 12. Configuration

Swagger and API docs disabled in deployed environments; tracing sampling reduced to 5% there
(it was 100% everywhere); actuator on its own port; explicit HikariCP sizing for
order-service, whose checkout path holds a connection across catalog calls. Java version
normalised to 21 across the parent pom, Dockerfiles, and CI.

---

## The ten remaining items

Each was then taken as far as it honestly goes. Evidence:
[`evidence/production-readiness-remaining-2026-09-09.json`](evidence/production-readiness-remaining-2026-09-09.json).

### Done and verified

**4. Reindexing is now atomic.** `products` is an alias over a timestamped index. A rebuild
fills a new index and moves the alias in a single Elasticsearch action; a failure leaves the
previous index serving and deletes its own leftovers. Writing the tests exposed a real gap:
the consistency check compared the pages it received only against each other, so a truncated
catalog pagination would have published a short index as "consistent". It now also checks the
catalog's declared total. 8 Elasticsearch tests, 4 rewritten unit tests.

**1. Payment reconciliation.** The charge call sat behind a retry policy with no idempotency
key, and recovery cancelled abandoned orders **without ever asking whether the charge went
through** — releasing stock for orders customers had paid for. Charges and refunds are now
keyed per order; recovery looks the charge up before cancelling, refunds it if it exists, and
defers rather than cancelling when the provider is unreachable, because not knowing is not
the same as knowing there was no charge. A concurrency test caught the mock's check-then-put
letting 8 retries create 8 charges. 9 provider tests, 6 workflow tests.

**7. Point-in-time recovery.** WAL archiving is enabled and
[`pitr-drill.sh`](../ops/backup/pitr-drill.sh) proves a restore to a chosen instant: a table
dropped after the target comes back, and the row written after it does not.

**9. The role migration.** Idempotent, and tested against a database built the old way:
23 checks covering data preservation, DDL by the new owner, and cross-database denial.
`REASSIGN OWNED` turned out to fail when the legacy role owns system objects, so ownership
transfers object by object.

**10. Frontend lint.** All 7 errors fixed; CI gates the whole app.

**6. Reproducible load testing.** Seeded query sets from the 10k corpus, declared cold / warm
/ mixed cache regimes, constant arrival rate (fixed VUs hide the degradation they exist to
find), and a summary manifest. The `hey` parser's error rate ignored dropped requests
entirely — it now counts them and flags the one-million-sample cap.

### Partly done, and honest about the rest

**2. Alert delivery.** Receivers read their destination from a mounted secret, so wiring a
pager is a secret change rather than an edit. Multi-window burn-rate alerting added. **No
destination is configured, so nobody is paged yet** — that needs your paging endpoint.

**3. SLO measurement.** Error-budget and latency recording rules exist, and the load harness
can produce comparable numbers. **Nothing has been measured against production traffic,
because there is none.** No number here is a track record.

**8. NetworkPolicy.** Now answered, and the answer is bad:
[`networkpolicy-enforcement.sh`](../ops/testing/networkpolicy-enforcement.sh) applied a
default-deny policy in a scratch namespace on the local cluster and **traffic still flowed**.
Every NetworkPolicy in `k8s/` is inert on docker-desktop. Pod-to-pod isolation rests on the
service tokens alone until an enforcing CNI (Calico, Cilium) is installed.

### Not done, deliberately

**5. Graded search evaluation.** The harness, rubric, and query set exist and are tested. What
is missing is graded relevance labels, and those are human judgement. Fabricating them would
make every number downstream — NDCG, recall, the mode comparison, any resume claim built on
them — a lie. This one needs a person to grade the pool, or an explicitly AI-labelled run
described as exactly that.

## Standing limitations

Accurate as of the review on 2026-09-09. Anything not listed here is covered by a test in
`ops/testing/verify-all.sh`.

**Cannot be closed by writing code**

- **Payments are a mock.** The API is tokenized and refuses card data, but no money moves.
  A real integration still needs processor webhooks, settlement reconciliation against
  processor reports, and dispute and chargeback handling. The mock keeps its idempotency
  records in a JVM map: not shared between replicas, not durable.
- **Nobody is paged.** Alertmanager routes by severity and reads each receiver's URL from a
  mounted secret that does not exist. 18 rules fire into a void until a real destination is
  wired and a test alert is observed arriving.
- **No production SLO has been measured.** `docs/SLO.md` states intended objectives. The SLI
  plumbing and alert transition have now been exercised under synthetic local traffic, but
  that is not a production track record.
- **The graded evaluation is AI-judged, not human-judged.** A full run now exists
  (`backend/search-service/evaluation/runs/2026-09-09-ai`, evidence class
  `ai_judged_pooled_evaluation`): 14 held-out queries, 271 blinded pairs, graded by Claude
  against the rubric. hybrid_rrf led on every metric (NDCG@10 0.756 against lexical 0.737)
  but its advantage is not distinguishable from zero. **The embedding service was a hashing
  stub, so the vector numbers measure a bag-of-words hash and say nothing about a trained
  model.** The queries are AI-authored and the labels AI-assigned; no human judged anything.
  See `evidence/search-evaluation-ai-judged-2026-09-09.json`.
- **Nothing has been deployed.** Staging and production overlays and an ordered deploy
  procedure exist and validate; no cluster has run them.

**Environment-dependent**

- **NetworkPolicy is unenforced on the local cluster.** Verified by
  `ops/testing/networkpolicy-enforcement.sh`: a default-deny policy was applied and traffic
  still flowed. The manifests are inert until an enforcing CNI is installed. Re-run that
  script on any cluster before relying on them.
- **One PostgreSQL instance and one Kafka broker.** Still one failure domain each.
  `docs/PRODUCTION-TOPOLOGY.md` describes the target; the manifests do not build it.

**Known gaps with no test**

- Backups are scheduled (`k8s/backup/cronjob.yaml`) and retention is enforced and tested,
  but the volume sits in the same cluster as the database. That is not an offsite copy, and
  no RPO or RTO is claimed.
- Packet loss, progressively slow responses, and disk-full behavior remain untested. There
  is still no performance regression gate or coverage floor.
- Resource requests and limits remain guesses. A first single-replica laptop benchmark now
  exists, but it is not a production capacity or cost model.
- No on-call rotation. The runbooks in `docs/runbooks/` exist but nobody owns them.

## The Kubernetes data tier (added 2026-09-09)

`k8s/configmap.yaml` had always pointed at hosts named `postgres`, `redis`, `elasticsearch`
and `kafka`, and nothing in the repository provided any of them — the stateful layer existed
only in `docker-compose.yml`. Applying `k8s/` produced eight services that could not reach a
database, and every validator passed, because the YAML was correct and only the deployment
was impossible.

`k8s/data-tier/` now supplies the four stores and `ops/testing/data-tier-deploy.sh` proves it
in a self-deleting scratch namespace: **seven checks, ending with all ten deployments starting,
passing readiness, a product retrieved through `catalog-service` from inside the
`api-gateway` pod, and the shop serving its index**. Evidence: `docs/evidence/kubernetes-data-tier-2026-09-09.json`.

Starting every service, rather than one, is the check that pays. Two of the four defects below
were found only because a service other than catalog was finally started in a cluster.

**Read that evidence before treating this as production.** It is one replica of each store:
one failure domain, no failover, an upgrade window that stops all five databases at once, and
a Kafka broker whose volume holds undelivered events at replication factor 1. The production
shape is managed instances, which is what `deploy/overlays/production-managed-data` selects —
it deletes all four StatefulSets and repoints every endpoint. That overlay is rendered and
checked, never deployed, because its endpoints are `REPLACE_ME` by design.

Three defects in committed code surfaced only by running it, each invisible to
`kubectl apply --dry-run=client`:

- **Three of five migration Jobs crash-looped.** A Job runs the service's own image, so
  Spring builds the service's whole context, but the Jobs carried none of the Secrets their
  Deployments carry. The documented release procedure would have blocked on `kubectl wait`
  for its full timeout. `ops/testing/migration-job.sh` missed it by exporting the credentials
  in the shell first: it tests the migrate profile, never the Job manifest's env wiring.
- **`JwtProperties` could not bind from environment variables**, so auth-service could not
  start under Kubernetes or Compose. A public `getPrivateKey()` on a `@ConfigurationProperties`
  JavaBean made the binder treat `security.jwt.private-key` as a property and call it partway
  through binding. 41 platform-security tests passed throughout, all binding from properties —
  never from the environment, which is the only form that triggers it.
- **The Kafka StatefulSet hardcoded the `gocommerce` namespace** in its advertised listener,
  which would have broken the pre-existing staging overlay.
- **`embedding-service` could not start**: the manifest named a model with nothing on disk and
  a read-only root filesystem, so every pod would try to download it into an unwritable home.
  Worse, the download was unpinned — the weights, and therefore every search result, could
  change without a deploy. The weights are now baked in at a pinned revision, with the build
  verifying the same `model.safetensors` sha256 the search evaluation evidence records.

What that adds up to: `kubectl apply -k k8s/` went from starting nothing to starting
everything, and four of the defects in the way were invisible to `kubectl apply --dry-run`.

## Chaos coverage beyond search (added 2026-09-09)

`ops/testing/chaos-drill-cart.sh` covers the case the search drill does not: Redis as the
system of record rather than as a cache. Evidence:
`docs/evidence/chaos-cart-2026-09-09.json`.

`CartService.getCart` does `findById(...).orElseGet(() -> new Cart(userId))`, where a miss
legitimately means "no cart yet". The drill exists so that a connection failure can never be
folded into that same path: a shopper with an intact cart would otherwise be shown an empty
one, with 200 OK, no error, and nothing in any dashboard to say it happened. Measured: **500
rather than a false empty cart, a slowest read of 2,026ms against roughly 60,000ms before the
timeout, and the cart intact after recovery.** That is also the first verification of the
cart-service Redis timeout, which until now was reasoned but undrilled.

`ops/testing/chaos-drill-order.sh` covers the money path, where the faults demand different
answers. Evidence: `docs/evidence/chaos-order-2026-09-09.json`. **PostgreSQL paused: 500, and no
phantom order left behind** — the dangerous answer being a 2xx carrying an order id for a row
that was never written. A subtler case now keeps PostgreSQL reachable for reads while making
transactions read-only: checkout still returned 500, persisted nothing, and recovered to 201
without restarting order-service. **Kafka paused: 201, checkout completed**, because the
outbox exists so the broker is not a hard dependency of taking money; queued events drained
when Kafka returned.

`ops/testing/chaos-drill-gateway.sh` isolates the edge limiter from the search cache. With
Redis stalled, three requests returned the upstream's non-empty 200 response and the slowest
took 1,051ms; rate enforcement returned after Redis recovered. The explicit decision is to
fail open for storefront reads, accepting that abuse protection is absent during the outage.

Still unexercised: packet loss, progressively slow dependencies, and disk-full behavior.

## The shop is deployable (added 2026-09-09)

The frontend had no container, no Kubernetes manifest, and no Compose entry — it existed only
as a Vite dev server in the README, so a cluster built from `k8s/` served nine APIs and nothing
a shopper could open. It now has `frontend/Dockerfile` (nginx-unprivileged, read-only root),
`k8s/frontend.yaml`, and ingress routing that sends `/api` to the gateway and everything else
to the single-page app.

One defect came out of it. `apiBase.js` read `VITE_API_BASE_URL || "http://localhost:8080"`,
and `||` treats an empty string as unset — so the same-origin configuration that lets one image
serve every environment would have compiled `localhost:8080` into the bundle, and every
shopper's browser would have called their own machine. It reads `??` now, and the built bundle
is asserted to contain zero occurrences of that host.

The Compose service has now also run: Docker health became healthy, `/healthz` returned 200,
and `/orders/1` returned the SPA shell with 200. That runtime check found a false-negative
healthcheck because BusyBox resolved `localhost` to IPv6 while nginx listened on IPv4; the
probe now targets `127.0.0.1`. Evidence: `docs/evidence/frontend-compose-2026-09-09.json`.

## Local application benchmark and SLI plumbing (added 2026-09-09)

The first benchmark against the real application is recorded in
`docs/evidence/load-benchmark-2026-09-09.json`. With one search replica and 600 generated
products, cold traffic sustained 25 requested RPS at p95 139ms, then missed a 40-RPS target
and reached p95 6.05s. Five explicitly preloaded warm keys sustained 2,000 RPS; a 5,000-RPS
target achieved 2,988 RPS and began returning transport errors. **These single-replica laptop
numbers do not transfer to production.** The catalog and query distribution are synthetic,
and the load generator shares the machine.

Running the rules against real scraped metrics found two silent failures. Healthy operation
had no 5xx series, so the error, availability, and budget recordings disappeared instead of
showing 0/1/0; the rules now preserve a zero vector. Separately, the gateway search fallback
returned a successful empty 200, hiding dependency failure from both shoppers and the 5xx
SLI; it now returns 503. All five requested recordings then existed with sane values, and
`GatewayErrorBudgetBurningFast` moved from pending to firing after its actual five-minute
hold under a paused search dependency. This verifies SLI plumbing under synthetic local
traffic; it does **not** measure a production SLO. Evidence:
`docs/evidence/slo-machinery-2026-09-09.json`.

## Journey load and AI-assessor agreement (added 2026-09-09)

`ops/k6/shopper-journey.js` adds an explicitly invented 50/30/20 distribution over browse,
search, cart, and completed checkout. It uses a real RS256 access token in application runs,
requires exact 201 for checkout, and labels the single synthetic identity and mock payment
limits. The harness ran every branch with zero failed synthetic steps; it is a contention
workload, not a model of shopper traffic.

The collapsed search test pool now has a second AI assessment over all 175 already judged
test-split pairs. Exact agreement is 84.57% and linear-weighted Cohen's kappa is 0.7454; all
27 disagreements are one grade apart. Both assessors are AI, and agreement is consistency,
not accuracy. No human labels were created or claimed. Evidence:
`docs/evidence/search-inter-assessor-agreement-2026-09-09.json`.

## The drills now run (added 2026-09-10)

Four drills existed and were wired to nothing: `chaos-drill-cart.sh`, `chaos-drill-order.sh`,
`chaos-drill-gateway.sh` and `data-tier-deploy.sh`. CI and `ops/testing/verify-all.sh` invoked
only the original search chaos drill, so those four would never have run again.

That is the same failure the backup CronJob comment describes: a drill nobody runs is a
document. Each of these already caught a real defect once — a false empty cart, a phantom
order, an edge that stalls on a black-holed rate limiter, a deployment that could not start —
and each of those defects would have returned silently.

They now run in both places. The deployment drill is its own CI job gated on
`vars.DEPLOYMENT_DRILL_ENABLED`, because it needs a cluster; it is declared rather than
omitted so the gap is visible instead of the drill quietly never running anywhere.

## Traffic model (added 2026-09-10)

`ops/k6/traffic-model.json` states the assumed shape of shopper traffic — session mix, query
head/tail split, think time, shopper isolation — in one file, so the assumptions can be argued
with and replaced rather than being buried in `shopper-journey.js`. Its own `status` field
reads `ASSUMED, NOT OBSERVED`.

The correction that prompted it: the journey converted **20% of sessions to checkout** against
a real e-commerce norm of roughly 1–3%. That overstates write load by about ten times. It is
1.2% now.

**The load benchmark is read-path only.** Every run in
`docs/evidence/load-benchmark-2026-09-09.json` is `search-load.js` against `/search`; no cart
or checkout write was exercised, so nothing there describes write capacity, database
contention or outbox throughput. `shopper-journey.js` covers those and **has not been run**.
That is the next benchmark worth taking.

