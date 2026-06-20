# Prod-Commerce

Production-inspired e-commerce microservices platform built with Java, Spring Boot, React, PostgreSQL, Redis, Kafka, Elasticsearch, Docker, Kubernetes, Prometheus and Grafana.

## Services

| Service | Port | Responsibility |
|---|---:|---|
| api-gateway | 8080 | Single entry point, routing, rate limiting |
| auth-service | 8081 | JWT auth, users, admin user APIs |
| catalog-service | 8082 | Products, catalog browsing, internal inventory APIs |
| cart-service | 8083 | Redis-backed customer cart |
| search-service | 8084 | Elasticsearch product search and caching |
| order-service | 8085 | Checkout, payment orchestration, inventory reservation, idempotency, transactional outbox |
| analytics-service | 8086 | Kafka consumer for order analytics |
| recommendation-service | 8087 | Kafka consumer for product recommendation stats |

## Production-readiness improvements

The checkout path has been hardened beyond a basic demo flow:

- order-service no longer trusts client-supplied `productName` or `unitPrice`; it fetches product snapshots from catalog-service.
- order-service decrements catalog inventory during checkout and compensates stock if payment fails.
- `POST /api/v1/orders` supports the `Idempotency-Key` header to prevent duplicate orders during client retries.
- order events are persisted through a transactional outbox before being published to Kafka.
- JPA schema generation now uses `ddl-auto: validate`; schema changes are managed through Flyway migrations.
- JWT secrets and database passwords are read from environment variables/Kubernetes Secrets.
- Kubernetes manifests use ConfigMaps, Secrets, readiness/liveness probes, resource requests/limits, ClusterIP services and an ingress entry.

## Running local infrastructure

Create a local env file:

```bash
cp .env.example .env
```

Update the passwords/secrets in `.env`, then start dependencies:

```bash
docker compose up -d postgres redis kafka zookeeper elasticsearch prometheus grafana
```

Each Spring service can then be run from its module, for example:

```bash
cd backend/order-service
SPRING_DATASOURCE_PASSWORD=<your-db-password> \
SECURITY_JWT_SECRET=<at-least-32-character-secret> \
mvn spring-boot:run
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
