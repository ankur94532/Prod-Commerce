# Prod-Commerce

Production-inspired e-commerce microservices platform with hybrid product search, Redis caching, Kafka event processing, idempotent checkout, transactional outbox, observability, resilience patterns, CI workflows and Kubernetes manifests.

This project is intentionally more than a CRUD demo. It focuses on the backend engineering concerns that show up in real commerce systems: search relevance, search latency, cache behavior, inventory consistency, payment failure handling, event delivery, service-to-service failures, metrics, tracing, logging, and repeatable benchmarks.

## Highlights

- Built an e-commerce microservices system with Java, Spring Boot, Spring Cloud Gateway, React, PostgreSQL, Redis, Kafka, Elasticsearch, Docker and Kubernetes.
- Implemented Elasticsearch-backed hybrid search using keyword retrieval, vector embeddings and Redis-backed result caching.
- Added a local embedding service using `BAAI/bge-small-en-v1.5` so product documents can be indexed with embedding vectors.
- Scaled local benchmark datasets from small development catalogs to larger production-like catalog sizes through configurable seeding.
- Added search relevance evaluation for `precision@k`, `recall@k`, `hit@k`, `MRR`, `MAP` and `NDCG@k`.
- Added engineering benchmarks for p50/p95/p99 latency, throughput, error rate, cache improvement, Redis hit ratio and reindex consistency.
- Hardened checkout with catalog-priced order snapshots, inventory reservation, payment failure compensation, idempotency keys and transactional outbox.
- Added Resilience4j circuit breakers, retries, timeouts and fallbacks around catalog, search and payment dependency calls.
- Added observability with Spring Boot Actuator, Micrometer, Prometheus, Grafana, OpenTelemetry, Jaeger, Loki and Promtail.
- Added Springdoc OpenAPI/Swagger docs, Testcontainers integration coverage and GitHub Actions workflows.

## Tech Stack

| Area | Technologies |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA, Hibernate, Maven |
| Gateway | Spring Cloud Gateway, Redis rate limiting, Gateway circuit breaker/retry filters |
| Frontend | React 19, Vite, React Router, Axios, Tailwind CSS |
| Auth | Spring Security, JWT/JJWT |
| Data stores | PostgreSQL, Redis, Elasticsearch |
| Messaging | Kafka, Zookeeper |
| Search/ML | Elasticsearch keyword search, vector search, hybrid search, Python embedding service, `BAAI/bge-small-en-v1.5` |
| Reliability | Resilience4j circuit breakers, retries, timeouts, fallbacks, idempotency, transactional outbox |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, Grafana, OpenTelemetry Collector, Jaeger, Loki, Promtail |
| Testing | JUnit, Mockito, H2, Testcontainers, search relevance smoke tests |
| DevOps | Docker, Docker Compose, Kubernetes manifests, GitHub Actions, k6 |
| Database migrations | Flyway |
| API docs | Springdoc OpenAPI, Swagger UI |

## Services

| Service | Port | Responsibility |
|---|---:|---|
| api-gateway | 8080 | Public entry point, routing, CORS, Redis-backed rate limiting, search route fallback |
| auth-service | 8081 | Registration, login, JWT issuing, user/admin APIs |
| catalog-service | 8082 | Product catalog, filters, product admin APIs, inventory APIs, product seeding |
| cart-service | 8083 | Redis-backed customer cart |
| search-service | 8084 | Elasticsearch keyword/vector/hybrid search, Redis search cache, reindexing |
| order-service | 8085 | Checkout, payment orchestration, inventory reservation, idempotency, transactional outbox |
| analytics-service | 8086 | Kafka consumer for order analytics summaries |
| recommendation-service | 8087 | Kafka consumer for product popularity/recommendation stats |
| embedding-service | 8090 | Local sentence-transformer embedding API for vector search |

## Architecture

```text
React frontend
    |
    v
Spring Cloud Gateway
    |
    +--> auth-service ------ PostgreSQL
    +--> catalog-service --- PostgreSQL
    |          |
    |          +-----------> search-service index update
    |
    +--> cart-service ------ Redis
    +--> search-service ---- Elasticsearch
    |          |             Redis search cache
    |          |             embedding-service
    |          +-----------> catalog-service during reindex
    |          +-----------> recommendation-service for popularity boosts
    |
    +--> order-service ----- PostgreSQL
               |
               +-----------> catalog-service internal inventory/product APIs
               +-----------> payment provider abstraction
               +-----------> transactional outbox
                               |
                               v
                             Kafka
                               |
                               +--> analytics-service
                               +--> recommendation-service
```

Supporting infrastructure:

```text
Spring Boot Actuator -> Prometheus -> Grafana
Spring services -> OpenTelemetry Collector -> Jaeger
Docker container logs -> Promtail -> Loki -> Grafana
```

## Search Architecture

Search is implemented as the default user-facing retrieval mode. The frontend calls:

```text
GET /api/v1/search?q=...&page=0&size=20
```

The backend defaults to hybrid search. Text-only and vector-only modes remain available as hidden/dev options for evaluation, but the user-facing frontend does not expose a mode selector.

Search flow:

1. Product data is stored in catalog-service/PostgreSQL.
2. The search-service reindex endpoint pages through catalog products instead of loading the full catalog at once.
3. Each product is converted into an Elasticsearch document.
4. Search text is built from product name, description, brand, category, attributes and tags.
5. The embedding service generates vectors for the product embedding text.
6. Elasticsearch stores keyword fields plus `searchEmbedding`.
7. A search request generates the query embedding locally.
8. Hybrid scoring combines Elasticsearch keyword score and vector similarity.
9. Redis caches query results so repeated queries avoid recomputing the same search path.

Important search endpoints:

```bash
curl "http://localhost:8080/api/v1/search?q=laptop&page=0&size=20"
curl -X POST "http://localhost:8084/api/v1/search/reindex"
```

Hidden/dev comparison modes:

```bash
curl "http://localhost:8080/api/v1/search?q=laptop&mode=text&page=0&size=20"
curl "http://localhost:8080/api/v1/search?q=laptop&mode=vector&page=0&size=20"
```

## Search Evaluation

The repo includes catalog-aware relevance evaluation instead of relying only on manual search checks.

Run a full generated-query evaluation:

```bash
BASE_URL=http://localhost:8080 COUNT=10000 \
  backend/search-service/scripts/relevance_report.sh
```

Metrics reported:

| Metric | Meaning |
|---|---|
| `precision@k` | How many top-k results are relevant |
| `recall@k` | How many expected relevant products were recovered |
| `hit@k` | Whether at least one relevant product appeared in top-k |
| `MRR` | How early the first relevant result appears |
| `MAP` | Ranking quality across relevant results |
| `NDCG@k` | Ranking quality with stronger weight for top positions |

Run a faster smoke check against live services:

```bash
BASE_URL=http://localhost:8080 COUNT=500 FAIL_UNDER=0.95 \
  backend/search-service/scripts/relevance_smoke.sh
```

The large report writes generated query cases, full JSON results and a markdown summary under `backend/search-service/scripts/`. These generated outputs are ignored by git by default so benchmark numbers are not accidentally committed without review.

Generated query families include exact product queries, compact product names, category aliases, brand/category combinations, attributes, commercial intent phrases, stock/deal intent phrases, typo variants and semantic queries.

## Search Engineering Benchmarks

The engineering benchmark script measures latency, throughput, caching and indexing behavior.

Short local run:

```bash
python3 backend/search-service/scripts/search_engineering_metrics.py \
  --base-url http://localhost:8080 \
  --search-service-url http://localhost:8084 \
  --dataset-size 1000 \
  --duration 15s \
  --concurrency 50
```

Production-like local benchmark:

```bash
python3 backend/search-service/scripts/search_engineering_metrics.py \
  --base-url http://localhost:8080 \
  --search-service-url http://localhost:8084 \
  --dataset-size 50000 \
  --warmup 5m \
  --duration 10m \
  --concurrency 50
```

Skip reindex after the first full run:

```bash
python3 backend/search-service/scripts/search_engineering_metrics.py \
  --skip-reindex \
  --dataset-size 1000 \
  --duration 15s \
  --concurrency 50
```

The script writes:

```text
backend/search-service/scripts/search-engineering-results.json
backend/search-service/scripts/search-engineering-results.md
```

Reported engineering metrics:

- p50, p95 and p99 search latency
- average latency
- requests/sec
- total requests
- error rate
- Redis cache hit ratio
- cold-vs-warm cache p95 improvement
- hybrid vs text comparison
- reindex duration and consistency
- dataset size

## k6 Load Tests

k6 scripts are included for cleaner load-test reporting than custom Python scripts.

Install:

```bash
brew install k6
```

Mixed-query search benchmark:

```bash
k6 run ops/k6/search-load.js
```

Production-style run with warmup and 10-minute measured window:

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

k6 writes JSON summaries to:

```text
ops/k6/results/
```

## Checkout and Order Reliability

The order-service is designed around correctness rather than trusting the client.

Checkout flow:

1. Client submits product IDs, quantities and payment details.
2. order-service validates the request and optional `Idempotency-Key`.
3. order-service fetches product snapshots from catalog-service.
4. Product name, unit price and currency are taken from catalog-service, not from the client.
5. order-service creates a pending order.
6. order-service decrements catalog inventory through internal inventory APIs.
7. If inventory reservation fails, checkout fails.
8. order-service charges the payment provider abstraction.
9. If payment fails or throws, order-service compensates inventory by incrementing previously decremented stock.
10. Successful orders are marked `PAID`.
11. An order-created event is persisted into the transactional outbox.
12. The outbox publisher publishes the event to Kafka.
13. analytics-service and recommendation-service consume order events.

Reliability details:

- `Idempotency-Key` prevents duplicate order creation on client retries.
- The request hash detects reuse of the same idempotency key with a different payload.
- Inventory compensation runs when payment fails after stock has already been reserved.
- The transactional outbox reduces DB/Kafka inconsistency risk.
- Outbox polling uses PostgreSQL `FOR UPDATE SKIP LOCKED`, which allows multiple publisher replicas to process safely.
- Internal catalog APIs require `X-Internal-Service-Token`.

## Resilience Strategy

Resilience4j is used where service-to-service calls can fail.

| Call path | Protection |
|---|---|
| gateway -> search-service | Gateway circuit breaker, retry and fallback response |
| catalog-service -> search-service indexing | Circuit breaker, retry, HTTP timeout, fallback that does not break admin writes |
| search-service -> catalog-service reindex fetch | Circuit breaker, retry, HTTP timeout, empty-page fallback |
| search-service -> recommendation-service popularity | Circuit breaker, retry, HTTP timeout, empty-popularity fallback |
| search-service -> Elasticsearch query path | Circuit breaker, retry and empty-result fallback |
| order-service -> catalog-service snapshot/stock APIs | Circuit breaker, retry, HTTP timeout, fail-closed fallback |
| order-service -> payment provider | Circuit breaker, retry and fail-closed fallback |

The fallbacks are intentionally conservative:

- Search can degrade to empty results.
- Popularity boosts can be skipped.
- Product admin writes should not fail only because search indexing is temporarily unavailable.
- Checkout should not fake successful payment or inventory reservation.

## Observability

The project includes metrics, logs and traces.

| Tool | Purpose | URL |
|---|---|---|
| Prometheus | Scrapes actuator metrics | http://localhost:9090 |
| Grafana | Dashboards and data exploration | http://localhost:3000 |
| Jaeger | Distributed tracing | http://localhost:16686 |
| Loki | Log storage/query API | http://localhost:3100 |
| Promtail | Ships Docker container logs to Loki | n/a |
| OpenTelemetry Collector | Receives OTLP traces and exports to Jaeger | http://localhost:4318/v1/traces |

Spring services expose actuator endpoints and Prometheus metrics. Tracing defaults to:

```text
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
```

Useful endpoints:

```bash
curl http://localhost:8084/actuator/health
curl http://localhost:8084/actuator/prometheus
curl http://localhost:8085/actuator/metrics
```

## API Documentation

Springdoc OpenAPI exposes API docs for Java services.

```text
/swagger-ui.html
/v3/api-docs
```

Examples:

```text
http://localhost:8081/swagger-ui.html
http://localhost:8082/swagger-ui.html
http://localhost:8084/swagger-ui.html
http://localhost:8085/swagger-ui.html
```

## Local Setup

Create a local env file:

```bash
cp .env.example .env
```

Update secrets and passwords in `.env`.

Start infrastructure:

```bash
docker compose up -d postgres redis zookeeper kafka elasticsearch prometheus grafana otel-collector jaeger loki promtail
```

Start the embedding service:

```bash
docker compose up -d embedding-service
```

The embedding service uses `EMBEDDING_MODEL_PATH`/`EMBEDDING_MODEL_NAME` and mounts `./models` into the container. The `models/` directory is intentionally ignored because model binaries are large and should not be committed.

Start backend services from their modules. Example:

```bash
cd backend/order-service
set -a
source ../../.env
set +a
CATALOG_SERVICE_URL=http://localhost:8082 mvn spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm install
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

## Catalog Size and Reindexing

Catalog seeding defaults to 1,000 products for fast local development.

For larger search benchmarks:

```bash
CATALOG_SEED_SIZE=50000 mvn spring-boot:run
```

For heavier production-like local benchmarking:

```bash
CATALOG_SEED_SIZE=100000 mvn spring-boot:run
```

Large catalogs should use batched search indexing:

```text
SEARCH_INDEXING_BATCH_SIZE=128
```

After catalog, search and embedding services are running, reindex into Elasticsearch:

```bash
curl -X POST http://localhost:8084/api/v1/search/reindex
```

## Configuration

Important environment variables:

| Variable | Purpose |
|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Local database credentials |
| `SECURITY_JWT_SECRET` | JWT signing secret |
| `INTERNAL_SERVICE_TOKEN` | Protects internal catalog APIs |
| `CATALOG_SEED_SIZE` | Number of seeded catalog products |
| `SEARCH_INDEXING_BATCH_SIZE` | Batch size for search indexing |
| `EMBEDDING_SERVICE_BASE_URL` | Embedding service URL |
| `EMBEDDING_MODEL_NAME` | Sentence-transformer model name |
| `EMBEDDING_DIMENSIONS` | Vector dimension count |
| `SEARCH_HTTP_CONNECT_TIMEOUT` | search-service outbound connect timeout |
| `SEARCH_HTTP_READ_TIMEOUT` | search-service outbound read timeout |
| `CATALOG_CLIENT_CONNECT_TIMEOUT` | order-service catalog connect timeout |
| `CATALOG_CLIENT_READ_TIMEOUT` | order-service catalog read timeout |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Trace export endpoint |
| `VITE_API_BASE_URL` | Frontend API base URL |

## Testing

Run backend tests:

```bash
cd backend
mvn test
```

Run selected modules:

```bash
cd backend
mvn -q -pl catalog-service,search-service,order-service,api-gateway test
```

Run frontend build:

```bash
cd frontend
npm install
npm run build
```

Test coverage includes:

- controller tests
- service tests
- security/JWT behavior
- search reindex behavior
- search edge cases for blank queries, unknown/zero-vector fallback, pagination normalization, result-size caps and filter construction
- catalog seed behavior
- H2-backed repository/service tests
- Testcontainers PostgreSQL repository coverage where Docker is available
- search relevance smoke workflow

## CI/CD

GitHub Actions workflows:

| Workflow | Purpose |
|---|---|
| `.github/workflows/ci.yml` | Backend tests and frontend build checks |
| `.github/workflows/search-relevance-smoke.yml` | Search relevance smoke workflow |
| `.github/workflows/docker-images.yml` | Docker image builds for backend services and embedding service |

The Docker image workflow builds service images and can push to GitHub Container Registry on `main`.

## Kubernetes

Kubernetes manifests are under `k8s/`.

They include:

- namespace
- ConfigMap
- Secret example
- service deployments
- ClusterIP services
- ingress
- readiness/liveness probes
- resource requests and limits

Create a real secret from the example first:

```bash
cp k8s/secrets.example.yaml k8s/secrets.yaml
# edit k8s/secrets.yaml and replace placeholder values
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/
```

The Kubernetes manifests assume Postgres, Kafka, Redis and Elasticsearch are reachable through the service names in `k8s/configmap.yaml`. In a real cloud deployment, point those values to managed services or cluster-internal DNS names.

## Resume-Ready Project Bullets

Use only measured numbers from your own benchmark runs.

Example wording:

```text
Built a production-inspired e-commerce search platform using Elasticsearch, Redis caching and embedding-backed hybrid retrieval; load-tested search over a configurable catalog under concurrent traffic with p95/p99 latency and error-rate tracking.
```

```text
Implemented idempotent checkout with catalog-priced order snapshots, inventory compensation and transactional outbox-based Kafka publishing for reliable order-event delivery.
```

```text
Evaluated search quality using Precision@10, Recall@10, NDCG@10, MRR and catalog-aware generated queries while tracking latency, throughput, cache hit ratio and reindex consistency.
```

```text
Added Resilience4j circuit breakers, retries, timeouts and fallbacks across catalog, search and payment dependency calls, with Prometheus metrics, Jaeger traces and Loki logs for observability.
```

## Known Limitations

This is a production-inspired local project, not a live production deployment.

Current limitations:

- Benchmark numbers are local unless explicitly run in a deployed environment.
- The payment provider is a mock abstraction, not a real Stripe integration.
- Elasticsearch, Redis, Kafka and PostgreSQL are local Docker services by default.
- Kubernetes manifests assume supporting infrastructure is reachable and do not provision managed cloud services.
- Testcontainers integration tests require a Docker environment that the JVM can access.
- Large embedding-backed reindexing is CPU and memory intensive on a laptop.
- Generated product data is useful for search/load testing but is still synthetic.

## Repo Hygiene

Do not commit generated folders or large local artifacts:

- `backend/**/target/`
- `frontend/node_modules/`
- `frontend/dist/`
- `.env`
- `.idea/`
- `models/`
- generated search evaluation outputs
- generated k6 result JSON files
