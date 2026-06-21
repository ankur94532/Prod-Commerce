# Prod-Commerce

Production-inspired e-commerce microservices platform built with Java, Spring Boot, React, PostgreSQL, Redis, Kafka, Elasticsearch, Docker, Kubernetes, Prometheus and Grafana.

## Services

| Service | Port | Responsibility |
|---|---:|---|
| api-gateway | 8080 | Single entry point, routing, Redis-backed rate limiting |
| auth-service | 8081 | JWT auth, users, admin user APIs |
| catalog-service | 8082 | Products, catalog browsing, internal inventory APIs |
| cart-service | 8083 | Redis-backed customer cart |
| search-service | 8084 | Elasticsearch keyword/vector/hybrid product search and Redis caching |
| order-service | 8085 | Checkout, payment orchestration, inventory reservation, idempotency, transactional outbox |
| analytics-service | 8086 | Kafka consumer for order analytics |
| recommendation-service | 8087 | Kafka consumer for product recommendation stats |
| embedding-service | 8090 | Local sentence-transformer embedding API for vector search |

## Production-readiness improvements

The checkout path has been hardened beyond a basic demo flow:

- order-service no longer trusts client-supplied `productName` or `unitPrice`; it fetches product snapshots from catalog-service.
- order-service decrements catalog inventory during checkout and compensates stock if payment fails or throws after stock reservation.
- catalog internal inventory/product snapshot APIs require `X-Internal-Service-Token`.
- `POST /api/v1/orders` supports the `Idempotency-Key` header and stores a request hash to detect mismatched retries.
- order events are persisted through a transactional outbox before being published to Kafka.
- outbox polling uses PostgreSQL `FOR UPDATE SKIP LOCKED` so multiple order-service replicas can publish safely.
- JPA schema generation uses `ddl-auto: validate`; schema changes are managed through Flyway migrations.
- JWT secrets, internal service token and database passwords are read from environment variables/Kubernetes Secrets.
- Kubernetes manifests use ConfigMaps, Secrets, readiness/liveness probes, resource requests/limits, ClusterIP services and an ingress entry.
- GitHub Actions runs backend tests and frontend builds.

## Local setup

Create a local env file:

```bash
cp .env.example .env
```

Update the passwords/secrets in `.env`, then start infrastructure:

```bash
docker compose up -d postgres redis zookeeper kafka elasticsearch prometheus grafana
```

Start the local embedding service when using vector or hybrid search:

```bash
docker compose up -d embedding-service
```

The embedding service uses `EMBEDDING_MODEL_NAME` and downloads/caches the model if it is not already available locally. The `models/` folder is intentionally ignored because model binaries are large and should not be committed.

After starting catalog, search and embedding services, reindex products so Elasticsearch stores embeddings:

```bash
curl -X POST http://localhost:8084/api/v1/search/reindex
```

Run each Spring service from its module. Example:

```bash
cd backend/order-service
set -a
source ../../.env
set +a
CATALOG_SERVICE_URL=http://localhost:8082 mvn spring-boot:run
```

Run the frontend:

```bash
cd frontend
npm install
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

## Search relevance evaluation

Run the search relevance evaluator against the live API:

```bash
python3 backend/search-service/scripts/relevance_eval.py \
  --base-url http://localhost:8080 \
  --count 10000 \
  --dump-queries backend/search-service/scripts/generated-queries.jsonl \
  --output-json backend/search-service/scripts/relevance-results.json
```

The evaluator generates catalog-aware queries and reports common search metrics: `precision@k`, `recall@k`, `hit@k`, `MRR`, `MAP`, and `NDCG@k`.
Use `--mode text` or `--mode vector` only for hidden/dev comparisons; omit `--mode` to test the default hybrid path used by the frontend.

For a faster local/CI smoke gate against running services:

```bash
BASE_URL=http://localhost:8080 COUNT=500 FAIL_UNDER=0.95 \
  backend/search-service/scripts/relevance_smoke.sh
```

## Kubernetes

Create a real secret from the example first:

```bash
cp k8s/secrets.example.yaml k8s/secrets.yaml
# edit k8s/secrets.yaml and replace placeholder values
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/
```

The Kubernetes manifests assume Postgres, Kafka, Redis and Elasticsearch are reachable through the service names in `k8s/configmap.yaml`. In a real cloud deployment, point those values to managed services or cluster-internal service DNS.

## Repo hygiene

Do not commit generated folders or large local artifacts:

- `backend/**/target/`
- `frontend/node_modules/`
- `frontend/dist/`
- `.env`
- `.idea/`
- `models/`
- generated search evaluation outputs
