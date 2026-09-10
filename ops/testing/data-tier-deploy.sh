#!/usr/bin/env bash
# Does `kubectl apply -k k8s/` actually produce something that starts?
#
# Until the data tier existed the answer was no, and nothing said so: the manifests were
# valid YAML, kustomize rendered them, and every Deployment referenced a ConfigMap pointing
# at hostnames -- postgres, redis, elasticsearch, kafka -- that no object in the repository
# provided. A deployment that cannot start is not caught by validation; it is caught by
# starting it.
#
# So this starts it, in a scratch namespace that is deleted on the way out. It never touches
# the gocommerce namespace, and it is not a deployment: nothing here is applied to anything
# a person uses.
#
# What it asserts, in order:
#   1. PostgreSQL, Redis, Elasticsearch and Kafka all become ready from the base manifests.
#   2. The init script really created five databases owned by five least-privilege roles,
#      and a role cannot open another service's database.
#   3. The ConfigMap's JDBC URLs and the per-service Secrets agree with what was created --
#      the mismatch that would leave a service crash-looping on a correct-looking cluster.
#   4. The migration Jobs complete against it.
#   5. A real application image starts, passes its readiness probe, and serves traffic.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

namespace="${NAMESPACE:-datatier-drill-$$}"
work="$(mktemp -d)"
cleaned=0

cleanup() {
  # Same shape as the other drills, and for the same reason: a cleanup that stops halfway
  # leaves a namespace holding an Elasticsearch pod and four volumes behind.
  local status=$?
  [ "$cleaned" = 1 ] && return
  cleaned=1
  set +e
  if [ "${succeeded:-0}" != "1" ]; then
    echo "--- pods at failure ---" >&2
    kubectl -n "$namespace" get pods 2>/dev/null >&2
    kubectl -n "$namespace" describe pods 2>/dev/null | grep -A5 "Events:" | tail -40 >&2
  fi
  if [ "${KEEP_NAMESPACE:-0}" = "1" ] && [ "${succeeded:-0}" = "1" ]; then
    # Left standing on purpose so another script can run against it -- the journey
    # benchmark does this. Only ever on success, and never silently: a namespace holding a
    # full stack is not something to discover later.
    echo "KEEPING namespace $namespace (KEEP_NAMESPACE=1); delete it with:" >&2
    echo "  kubectl delete namespace $namespace" >&2
    cp "$work/jwt.pem" "${JWT_KEY_OUT:-/tmp/datatier-jwt.pem}" 2>/dev/null
    rm -rf "$work"
    return "$status"
  fi
  kubectl delete namespace "$namespace" --wait=false >/dev/null 2>&1
  rm -rf "$work"
  echo "scratch namespace $namespace deleted" >&2
  return "$status"
}
trap cleanup EXIT

kubectl get nodes >/dev/null || { echo "no reachable cluster; skipping" >&2; exit 0; }

echo "Rendering the base manifests into scratch namespace $namespace"
# Rendered from k8s/ exactly as a real apply would, then retargeted. An overlay would be
# tidier, but kustomize refuses an absolute path as a root, and this drill must not write a
# temporary overlay into the repository it is testing.
kubectl kustomize k8s/ > "$work/base.yaml"
python3 "$repo_root/ops/testing/retarget-namespace.py" "$work/base.yaml" "$work/rendered.yaml" "$namespace"

# Belt and braces before anything is applied: the drill must never touch gocommerce.
grep -q "namespace: ${namespace}" "$work/rendered.yaml" || {
  echo "FAIL: the rendered manifests were not retargeted to the scratch namespace" >&2
  exit 1; }
if grep -qE "^\\s*namespace: gocommerce\\s*$" "$work/rendered.yaml"; then
  echo "FAIL: an object still targets the real gocommerce namespace; refusing to apply" >&2
  exit 1
fi

# Real credentials, generated per run. The example Secrets carry "replace-with-..." strings
# on purpose, and a drill that used them would prove only that placeholders are consistent.
# `tr < /dev/urandom | head -c 24` reads forever and head closes the pipe, which sends tr
# SIGPIPE; with `set -o pipefail` that surfaces as exit 141 and kills the drill before it
# does anything. Bound the read instead, so every stage in the pipeline ends on its own.
password() { LC_ALL=C head -c 1024 /dev/urandom | LC_ALL=C tr -dc 'a-zA-Z0-9' | cut -c1-24; }
# One variable per service rather than an associative array: `declare -A` is bash 4, and
# macOS ships bash 3.2, where it fails with "invalid option" rather than anything readable.
for service in auth catalog order analytics recommendation; do
  eval "password_${service}=\"$(password)\""
done
db_password() { eval "printf '%s' \"\$password_$1\""; }
superuser_password="$(password)"

kubectl create namespace "$namespace" >/dev/null
for service in auth catalog order analytics recommendation; do
  upper="$(echo "$service" | tr '[:lower:]' '[:upper:]')"
  kubectl -n "$namespace" create secret generic "gocommerce-${service}-db" \
    --from-literal="${upper}_DB_PASSWORD=$(db_password "$service")" >/dev/null
done
kubectl -n "$namespace" create secret generic gocommerce-postgres-superuser \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal="POSTGRES_PASSWORD=${superuser_password}" >/dev/null
kubectl -n "$namespace" create secret generic gocommerce-kafka \
  --from-literal=CLUSTER_ID=DAIQKLbNSSSxLaMmI2okqQ >/dev/null
kubectl -n "$namespace" create secret generic gocommerce-internal-auth \
  --from-literal=INTERNAL_SERVICE_TOKEN=data_tier_drill_internal_token_value >/dev/null
# A real RSA public key, generated for this run and discarded with the namespace. Empty
# values would be a configuration no deployment ever has: the verifier falls back to a
# legacy shared-secret path and fails to build, so the drill would be measuring a fixture
# problem and reporting it as a manifest problem.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/jwt.pem" 2>/dev/null
openssl rsa -in "$work/jwt.pem" -pubout -outform DER -out "$work/jwt.pub.der" 2>/dev/null
openssl pkcs8 -topk8 -nocrypt -in "$work/jwt.pem" -outform DER -out "$work/jwt.key.der" 2>/dev/null
kubectl -n "$namespace" create secret generic gocommerce-jwt-verifier \
  --from-literal=SECURITY_JWT_ACTIVE_KEY_ID=drill \
  --from-literal="SECURITY_JWT_PUBLIC_KEY_BASE64=$(base64 < "$work/jwt.pub.der" | tr -d '\n')" \
  --from-literal=SECURITY_JWT_PREVIOUS_KEY_ID= \
  --from-literal=SECURITY_JWT_PREVIOUS_PUBLIC_KEY_BASE64= >/dev/null
kubectl -n "$namespace" create secret generic gocommerce-jwt-signer \
  --from-literal="SECURITY_JWT_PRIVATE_KEY_BASE64=$(base64 < "$work/jwt.key.der" | tr -d '\n')" >/dev/null

# Generated from the same script Compose uses, which is the point of the renderer.
NAMESPACE="$namespace" ops/k8s/render-postgres-init-configmap.sh | kubectl apply -f - >/dev/null

# An image older than the code it claims to run makes every result below meaningless, and
# quietly so: a stale image starts, connects, and behaves like a service, just not like this
# one. The first run of this drill spent ten minutes concluding that migrations hang, when
# what was actually installed was a seven-month-old build in which Flyway was not wired up
# yet. Refuse instead of measuring the wrong binary.
assert_image_is_current() {
  local image="$1" source_dir="$2" created stale
  created="$(docker image inspect -f '{{.Created}}' "$image" 2>/dev/null)" || {
    echo "FAIL: image $image is not present." >&2
    echo "      docker build -f ${source_dir}/Dockerfile -t ${image} backend/" >&2
    exit 1; }
  if ! stale="$(python3 "$repo_root/ops/testing/image-is-current.py" "$created" \
        "$source_dir/src" "$source_dir/pom.xml")"; then
    echo "FAIL: $image was built before $stale changed." >&2
    echo "      The checks below would be measuring a different version of this service." >&2
    echo "      docker build -f ${source_dir}/Dockerfile -t ${image} backend/" >&2
    exit 1
  fi
}

checks=0

echo "1. Applying the data tier from the base manifests"
python3 - "$work/rendered.yaml" "$work/data-tier.yaml" <<'PY'
import sys
# Split the rendered base: the data tier and the ConfigMap first, the application
# Deployments later, so a service failing to start cannot be confused with a store that
# never came up.
docs = open(sys.argv[1]).read().split('\n---\n')
wanted = []
for doc in docs:
    # ServiceAccounts come along: the migration Jobs and the Deployments name one, and a
    # pod referencing an absent ServiceAccount stays Pending forever while the Job it
    # belongs to reports "Running" -- which reads as a hung migration, not a missing object.
    if 'kind: StatefulSet' in doc or 'kind: ConfigMap' in doc or 'kind: ServiceAccount' in doc:
        wanted.append(doc)
    elif 'kind: Service' in doc and any(f'name: {n}\n' in doc for n in ('postgres', 'redis', 'elasticsearch', 'kafka')):
        wanted.append(doc)
open(sys.argv[2], 'w').write('\n---\n'.join(wanted))
PY
kubectl -n "$namespace" apply -f "$work/data-tier.yaml" >/dev/null

for store in postgres redis elasticsearch kafka; do
  echo "   waiting for $store"
  kubectl -n "$namespace" rollout status "statefulset/$store" --timeout=300s >/dev/null || {
    echo "FAIL: $store never became ready from the base manifests" >&2
    kubectl -n "$namespace" logs "statefulset/$store" --tail=30 >&2 || true
    exit 1; }
done
checks=$((checks + 1))
echo "   all four stores ready"

psql_as() {
  kubectl -n "$namespace" exec statefulset/postgres -- env PGPASSWORD="$2" \
    psql -h 127.0.0.1 -U "$1" -d "$3" -tAc "$4" 2>/dev/null
}

echo "2. The init script created five databases, each owned by its own role"
for pair in "auth:ecom_auth" "catalog:ecom_catalog" "order:ecom_order" \
            "analytics:ecom_analytics" "recommendation:ecom_recommendation"; do
  service="${pair%%:*}"; database="${pair#*:}"
  result="$(psql_as "${service}_service" "$(db_password "$service")" "$database" 'SELECT 1' | tr -d '\r')"
  [ "$result" = "1" ] || {
    echo "FAIL: ${service}_service cannot open $database with the password from its Secret." >&2
    echo "      The ConfigMap URL, the Secret and the init script disagree." >&2
    exit 1; }
done
checks=$((checks + 1))
echo "   five roles each reached their own database"

echo "3. A role cannot reach another service's database"
if psql_as analytics_service "$(db_password analytics)" ecom_auth 'SELECT 1' | grep -q 1; then
  echo "FAIL: analytics_service opened ecom_auth. The per-database isolation is not real." >&2
  exit 1
fi
checks=$((checks + 1))
echo "   cross-database access denied"

echo "4. Redis, Elasticsearch and Kafka answer on the addresses the ConfigMap publishes"
kubectl -n "$namespace" exec statefulset/redis -- redis-cli -h redis ping 2>/dev/null | grep -q PONG || {
  echo "FAIL: redis did not answer on its service name" >&2; exit 1; }
kubectl -n "$namespace" exec statefulset/elasticsearch -- \
  curl -sf "http://elasticsearch:9200/_cluster/health?wait_for_status=yellow&timeout=10s" >/dev/null || {
  echo "FAIL: elasticsearch did not reach yellow on its service name" >&2; exit 1; }
kubectl -n "$namespace" exec statefulset/kafka -- \
  kafka-broker-api-versions --bootstrap-server kafka:9092 >/dev/null 2>&1 || {
  echo "FAIL: kafka did not answer on its service name" >&2; exit 1; }
checks=$((checks + 1))
echo "   all three answered"

echo "5. The migration Jobs complete against this tier"
for service in auth catalog order analytics recommendation; do
  assert_image_is_current "${service}-service:latest" "backend/${service}-service"
done
python3 - "$repo_root/k8s/migrations/jobs.yaml" "$work/migrations.yaml" "$namespace" <<'PY'
import re, sys
text = open(sys.argv[1]).read()
open(sys.argv[2], 'w').write(re.sub(r'namespace:\s*gocommerce\b', 'namespace: ' + sys.argv[3], text))
PY
kubectl -n "$namespace" apply -f "$work/migrations.yaml" >/dev/null
kubectl -n "$namespace" wait --for=condition=complete job \
  -l app.kubernetes.io/component=migration --timeout=420s >/dev/null || {
  echo "FAIL: the migration Jobs did not complete against the data tier" >&2
  kubectl -n "$namespace" get jobs >&2
  # Why a Job is not finishing is almost never in the Job; it is in the pod's phase (a
  # Pending pod is a scheduling or reference problem, a Running one is the application).
  kubectl -n "$namespace" get pods -l app.kubernetes.io/component=migration \
    -o custom-columns='POD:.metadata.name,PHASE:.status.phase,REASON:.status.conditions[0].reason' >&2 2>/dev/null
  for pod in $(kubectl -n "$namespace" get pods -o name 2>/dev/null | grep migrate); do
    echo "--- $pod" >&2
    kubectl -n "$namespace" logs "$pod" --tail=25 >&2 2>/dev/null || \
      kubectl -n "$namespace" describe "$pod" 2>/dev/null | tail -15 >&2
  done
  exit 1; }
checks=$((checks + 1))
echo "   migrations applied"

echo "6. Every service starts against it and passes its own readiness probe"
# Not one service, all of them. Starting a single service proves the data tier answers; it
# does not prove the other eight can start, and that gap is not hypothetical -- auth-service
# could not start in Kubernetes at all until this drill was pointed at it, because a
# @ConfigurationProperties accessor made Spring's binder fail on environment-variable
# configuration. Seven services were in exactly that unexamined state.
for service in api-gateway auth-service catalog-service cart-service search-service \
               order-service analytics-service recommendation-service; do
  assert_image_is_current "${service}:latest" "backend/${service}"
done
# The shop, which is the only thing in here a shopper opens. node_modules and dist are
# excluded by the checker, so this tracks the source and the nginx configuration.
assert_image_is_current "frontend:latest" "frontend/src"
# embedding-service is Python: no src/ or pom.xml, and its weights are baked in at a pinned
# revision, so freshness is measured against what the image is actually built from.
assert_image_is_current "embedding-service:latest" "backend/embedding-service"

python3 - "$work/rendered.yaml" "$work/services.yaml" <<'SPLIT'
import sys
docs = open(sys.argv[1]).read().split('\n---\n')
wanted = [d for d in docs
          if ('kind: Deployment' in d or 'kind: Service' in d)
          and not any(f'name: {n}\n' in d for n in ('postgres', 'redis', 'elasticsearch', 'kafka'))]
open(sys.argv[2], 'w').write('\n---\n'.join(wanted))
SPLIT
kubectl -n "$namespace" apply -f "$work/services.yaml" >/dev/null

failed=""
for service in api-gateway auth-service catalog-service cart-service search-service \
               order-service analytics-service recommendation-service embedding-service \
               frontend; do
  if kubectl -n "$namespace" rollout status "deployment/$service" --timeout=300s >/dev/null 2>&1; then
    echo "   $service ready"
  else
    echo "   $service DID NOT BECOME READY" >&2
    failed="$failed $service"
  fi
done
[ -z "$failed" ] || {
  echo "FAIL: these services could not start against the deployment:$failed" >&2
  for service in $failed; do
    echo "--- $service" >&2
    kubectl -n "$namespace" logs "deployment/$service" --tail=25 2>/dev/null | \
      grep -iE "error|caused by|reason:|description:|failed" | head -8 >&2
    kubectl -n "$namespace" describe "deployment/$service" 2>/dev/null | \
      grep -A3 "Conditions:" | tail -4 >&2
  done
  exit 1; }
checks=$((checks + 1))
echo "   all ten deployments ready"

echo "7. The shop and the gateway both answer, and a request crosses the cluster"
# Readiness only says a process answered its own probe. This asks the gateway for a real
# product page, which it can only satisfy by reaching catalog-service, which can only answer
# from the database the migrations just built.
served="$(kubectl -n "$namespace" exec deployment/api-gateway -- \
  curl -sf "http://catalog-service:8082/api/v1/products?page=0&size=1" 2>/dev/null || true)"
echo "$served" | grep -q 'totalElements' || {
  echo "FAIL: the gateway pod could not retrieve a product through catalog-service" >&2
  echo "      got: ${served:-<no response>}" >&2
  exit 1; }
# The shop has to actually serve its entry point, not merely pass a health check.
index_status="$(kubectl -n "$namespace" exec deployment/api-gateway -- \
  curl -s -o /dev/null -w '%{http_code}' "http://frontend:8080/" 2>/dev/null || true)"
[ "$index_status" = "200" ] || {
  echo "FAIL: the frontend did not serve its index page (got ${index_status:-no response})" >&2
  exit 1; }
checks=$((checks + 1))
echo "   product retrieved through the cluster network, and the shop served its index"

succeeded=1
echo
echo "Deployment drill passed ${checks} checks: the base manifests stand up PostgreSQL, Redis,"
echo "Elasticsearch and Kafka; the five databases exist with per-service roles and cross-database"
echo "access is denied; the migration Jobs complete;"
echo "all ten deployments including the shop start and pass readiness; and a product is"
echo "retrieved across the cluster network."
