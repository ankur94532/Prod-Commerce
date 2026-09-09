# Production readiness — Tier 1 and Tier 2

Completed 2026-09-09. All work is local and uncommitted; **nothing was deployed**, and the
running local cluster was not modified. Evidence:
[`evidence/production-readiness-2026-09-09.json`](evidence/production-readiness-2026-09-09.json).

Run everything with `ops/testing/verify-all.sh` (fifteen steps, all passing).

| Suite | Command | Result |
| --- | --- | --- |
| Backend unit and API | `mvn -f backend/pom.xml test` | 257 tests, 0 failures, 50 Docker-gated skips (was 160) |
| Checkout reliability | `ops/testing/checkout-reliability.sh` | 36 PostgreSQL + 10 frontend, 0 skips (was 24) |
| Database role isolation | `ops/testing/database-isolation.sh` | 26 checks |
| Database role migration | `ops/testing/database-role-migration.sh` | 23 checks |
| Search Elasticsearch contracts | `ops/testing/search-retrieval.sh` | 18 executions |
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

- The payment provider is a mock with in-process memory and no settlement reconciliation.
- No alert reaches a human until Alertmanager has a destination.
- No SLO has been measured against real traffic.
- NetworkPolicy is unenforced on the local cluster.
- One PostgreSQL instance and one Kafka broker: still one failure domain each.
- WAL archiving has no retention policy; the archive grows without bound.
- Nothing has been deployed.
