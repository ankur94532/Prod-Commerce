#!/usr/bin/env bash
# Runs every verification suite in the repository and prints one summary. Each step either
# passes or fails the whole run: there are no advisory steps, because a check nobody has to
# pass is not a check.
#
# Requires Docker for the PostgreSQL, Elasticsearch, load-harness, and config steps.
#
# Not included, because it needs a live cluster and tells you about that cluster rather
# than about this code: ops/testing/networkpolicy-enforcement.sh
set -uo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

declare -a NAMES=()
declare -a RESULTS=()
declare -a DETAILS=()
failed=0

run() {
  local name="$1"
  shift
  local log
  log="$(mktemp)"
  printf '\n=== %s ===\n' "$name"
  if "$@" > "$log" 2>&1; then
    NAMES+=("$name"); RESULTS+=("pass"); DETAILS+=("$(tail -1 "$log")")
    echo "pass"
  else
    NAMES+=("$name"); RESULTS+=("FAIL"); DETAILS+=("$(tail -3 "$log" | tr '\n' ' ')")
    failed=1
    echo "FAIL"
    tail -20 "$log"
  fi
  rm -f "$log"
}

backend_tests() { mvn -f backend/pom.xml verify -q; }
frontend_tests() { (cd frontend && npm test); }
frontend_lint() { (cd frontend && npm run lint); }
frontend_build() { (cd frontend && npm run build); }
frontend_audit() { (cd frontend && npm audit --omit=dev --audit-level=high); }
kubernetes_manifests() {
  kubectl apply --dry-run=client -k k8s &&
    kubectl apply --dry-run=client -k k8s/migrations &&
    kubectl apply --dry-run=client -k k8s/seeds &&
    kubectl apply --dry-run=client -k deploy/overlays/staging &&
    kubectl apply --dry-run=client -k deploy/overlays/staging-migrations &&
    kubectl apply --dry-run=client -k deploy/overlays/production &&
    kubectl apply --dry-run=client -k deploy/overlays/production-migrations
}
compose_config() { set -a; . ./.env.example; set +a; docker compose config; }
prometheus_rules() {
  docker run --rm -v "$repo_root/ops/prometheus:/etc/prometheus:ro" \
    --entrypoint promtool prom/prometheus:v2.55.1 check config /etc/prometheus/prometheus.yml
}
alertmanager_config() {
  docker run --rm -v "$repo_root/ops/alertmanager:/etc/am:ro" \
    --entrypoint amtool prom/alertmanager:v0.27.0 check-config /etc/am/alertmanager.yml
}

run "Backend unit and API tests"        backend_tests
run "Checkout reliability (PostgreSQL)" ops/testing/checkout-reliability.sh
run "Database role isolation"           ops/testing/database-isolation.sh
run "Database role migration"           ops/testing/database-role-migration.sh
run "Schema migration job"              ops/testing/migration-job.sh
run "Consumer-driven HTTP contracts"    ops/testing/contracts.sh
run "Ordered deployment procedure"      ops/testing/deploy-procedure.sh
run "Search retrieval (Elasticsearch)"  ops/testing/search-retrieval.sh
run "Graded evaluation harness"         ops/testing/search-evaluation.sh
run "Load-test harness"                 ops/testing/load-harness.sh
run "Chaos drill (search dependencies)"  ops/testing/chaos-drill.sh
# The other three chaos drills and the deployment drill were written but wired to nothing,
# so only the search one would ever have run again. A drill nobody runs is a document: the
# defects these guard against -- a false empty cart, a phantom order, a deployment that
# cannot start -- come back silently once nothing checks for them.
run "Chaos drill (cart store)"          ops/testing/chaos-drill-cart.sh
run "Chaos drill (order money path)"    ops/testing/chaos-drill-order.sh
run "Chaos drill (gateway limiter)"     ops/testing/chaos-drill-gateway.sh
run "Kubernetes deployment drill"       ops/testing/data-tier-deploy.sh
# The write path end to end. Not a capacity gate -- the numbers vary with the machine --
# but "every cart write and every checkout succeeded" is a real assertion, and it is the
# only check here that exercises a write under concurrency.
run "Journey benchmark (write path)"    ops/testing/journey-benchmark.sh
run "Point-in-time recovery drill"      ops/backup/pitr-drill.sh
run "Backup retention policy"           ops/testing/backup-retention.sh
run "Frontend tests"                    frontend_tests
run "Frontend lint"                     frontend_lint
run "Frontend build"                    frontend_build
run "Frontend production dependency audit" frontend_audit
run "Kubernetes manifests"              kubernetes_manifests
run "Compose configuration"             compose_config
run "Prometheus config and alert rules" prometheus_rules
run "Alertmanager configuration"        alertmanager_config

printf '\n================ summary ================\n'
for i in "${!NAMES[@]}"; do
  printf '%-40s %s\n' "${NAMES[$i]}" "${RESULTS[$i]}"
done
printf '=========================================\n'

if [ "$failed" -ne 0 ]; then
  echo "One or more verification steps failed."
  exit 1
fi
echo "All verification steps passed."
