#!/usr/bin/env bash
# The first write-path benchmark.
#
# ops/k6/search-load.js measured reads: /search, with cache regimes. Nothing had ever
# measured a write. So there is no number anywhere for checkout throughput, for contention
# on the order table, or for how fast the outbox drains under load -- which are the three
# things that decide whether this system can take money at any particular rate.
#
# This stands the whole system up in a scratch namespace, seeds a catalog, builds the search
# index, and drives ops/k6/shopper-journey.js through the gateway. The journey shape comes
# from ops/k6/traffic-model.json, whose every parameter is assumed rather than observed and
# which says so itself.
#
# What the result is: a measurement of this code on one laptop, one replica per service,
# against a generated catalog, under an invented traffic shape.
# What it is not: a capacity plan.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

namespace="journey-bench-$$"
work="$(mktemp -d)"
results="ops/k6/results/journey-$(date -u +%Y%m%dT%H%M%SZ)"
cleaned=0

cleanup() {
  local status=$?
  [ "$cleaned" = 1 ] && return
  cleaned=1
  set +e
  kubectl delete namespace "$namespace" --wait=false >/dev/null 2>&1
  rm -rf "$work"
  echo "scratch namespace $namespace deleted" >&2
  return "$status"
}
trap cleanup EXIT

echo "Standing the system up (this reuses the deployment drill, which asserts it actually works)"
KEEP_NAMESPACE=1 NAMESPACE="$namespace" JWT_KEY_OUT="$work/jwt.pem" \
  ops/testing/data-tier-deploy.sh || {
  echo "FAIL: the deployment drill did not pass, so there is nothing trustworthy to benchmark" >&2
  exit 1; }
[ -s "$work/jwt.pem" ] || { echo "FAIL: the drill did not hand over its signing key" >&2; exit 1; }

echo "Seeding the catalog"
python3 ops/testing/retarget-namespace.py <(kubectl kustomize k8s/seeds) "$work/seed.yaml" "$namespace" 2>/dev/null \
  || sed "s/namespace: gocommerce/namespace: $namespace/" k8s/seeds/job.yaml > "$work/seed.yaml"
kubectl -n "$namespace" apply -f "$work/seed.yaml" >/dev/null
kubectl -n "$namespace" wait --for=condition=complete job/catalog-service-seed --timeout=600s >/dev/null || {
  echo "FAIL: the catalog seed job did not complete" >&2
  kubectl -n "$namespace" logs job/catalog-service-seed --tail=25 >&2 || true
  exit 1; }

catalog_total="$(kubectl -n "$namespace" exec deployment/api-gateway -- \
  curl -sf "http://catalog-service:8082/api/v1/products?page=0&size=1" 2>/dev/null \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("totalElements", 0))' 2>/dev/null || echo 0)"
[ "${catalog_total:-0}" -gt 0 ] || {
  echo "FAIL: the catalog is serving ${catalog_total} products; a journey cannot pick one" >&2; exit 1; }
echo "   catalog is serving ${catalog_total} products"

echo "Building the search index"
kubectl -n "$namespace" exec deployment/api-gateway -- \
  curl -sf -X POST "http://search-service:8084/api/v1/search/reindex" \
  -H "X-Internal-Service-Token: data_tier_drill_internal_token_value" >/dev/null 2>&1 || true

# A real token, for the same reason the chaos drills need one: cart and checkout authenticate
# every request, so an unauthenticated benchmark would measure the 401 path.
# One token per synthetic shopper. Cart and checkout authorise on the subject matching the
# path, so a single token cannot act as twenty shoppers -- it produced a 403 on every cart
# write and therefore zero checkouts.
shopper_count="${SHOPPER_COUNT:-20}"
python3 - "$work" "$shopper_count" <<'TOKENS' > "$work/tokens.json"
import json, subprocess, sys
work, count = sys.argv[1], int(sys.argv[2])
tokens = []
for i in range(count):
    subject = f"load-shopper-{i}"
    token = subprocess.run(
        ["python3", "ops/testing/mint-access-token.py", f"{work}/jwt.pem", "drill", subject, "USER", "7200"],
        check=True, capture_output=True, text=True).stdout.strip()
    tokens.append({"subject": subject, "token": token})
print(json.dumps(tokens))
TOKENS
tokens_json="$(cat "$work/tokens.json")"
token="$(python3 -c "import json,sys; print(json.load(open('$work/tokens.json'))[0]['token'])")"
echo "   minted ${shopper_count} shopper tokens"

mkdir -p "$results"
target_rps="${TARGET_RPS:-10}"
duration="${BENCHMARK_DURATION:-3m}"
echo "Driving the journey at ${target_rps} sessions/s for ${duration}"

# The script goes in as a ConfigMap rather than down kubectl's stdin. `kubectl run -i`
# has to attach to the pod, and that attach times out often enough to be useless in a
# script -- it failed here before this was changed, reporting "timed out waiting for the
# condition" and producing no output at all.
python3 - > "$work/journey.js" <<'INLINE'
import pathlib
script = pathlib.Path('ops/k6/shopper-journey.js').read_text()
model = pathlib.Path('ops/k6/traffic-model.json').read_text()
# The model is inlined too, so the pod needs no second mount.
print(script.replace("JSON.parse(open(__ENV.TRAFFIC_MODEL || './traffic-model.json'))",
                     f"({model})"))
INLINE

kubectl -n "$namespace" create configmap k6-journey --from-file=journey.js="$work/journey.js" >/dev/null

cat > "$work/k6-job.yaml" <<K6JOB
apiVersion: batch/v1
kind: Job
metadata:
  name: k6-journey
  namespace: ${namespace}
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: k6
          image: grafana/k6:latest
          args: ["run", "--quiet", "/scripts/journey.js"]
          env:
            - name: BASE_URL
              value: "http://api-gateway:8080"
            - name: ACCESS_TOKEN
              value: "${token}"
            - name: ACCESS_TOKENS
              value: '${tokens_json}'
            - name: TARGET_RPS
              value: "${target_rps}"
            - name: BENCHMARK_DURATION
              value: "${duration}"
            # The summary goes to stdout so it lands in the pod log. Its default is a path
            # under ops/k6/results, which exists in the repository and not in this container:
            # k6 then reported "could not open ... no such file or directory" and the run
            # produced a one-line message with no per-step detail at all.
            - name: SUMMARY_OUT
              value: "/dev/stdout"
          volumeMounts:
            - name: scripts
              mountPath: /scripts
      volumes:
        - name: scripts
          configMap:
            name: k6-journey
K6JOB
kubectl -n "$namespace" apply -f "$work/k6-job.yaml" >/dev/null

# Generous: the job runs for BENCHMARK_DURATION plus start-up and teardown.
kubectl -n "$namespace" wait --for=condition=complete job/k6-journey --timeout=900s >/dev/null 2>&1 \
  || kubectl -n "$namespace" wait --for=condition=failed job/k6-journey --timeout=10s >/dev/null 2>&1 || true
kubectl -n "$namespace" logs job/k6-journey --tail=-1 > "$work/k6.out" 2>&1 || true

mkdir -p "$results"
cp "$work/k6.out" "$results/k6-output.txt"
grep -qE "checks|http_req_duration|journey_checkout" "$work/k6.out" || {
  echo "FAIL: k6 produced no usable output; full log kept at $results/k6-output.txt" >&2
  tail -40 "$work/k6.out" >&2; exit 1; }

echo
echo "=== journey benchmark output ==="
grep -E "checks|http_req_duration|http_reqs|journey_|iterations" "$work/k6.out" | head -20
echo
echo "Raw output: $results/k6-output.txt"
echo "This is one laptop, one replica per service, a generated catalog and an invented"
echo "traffic shape. It is a measurement of this code, not a capacity plan."
