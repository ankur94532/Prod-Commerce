#!/usr/bin/env bash
# Verifies the load-test harness itself against a stub server: query-set determinism, the
# cold and warm cache regimes, transport-error accounting, and the shape of the summary it
# writes. This measures the harness, not the application, and deliberately needs no running
# stack. Real runs go through ops/k6/README.md.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

work="$(mktemp -d)"
# k6 runs in a container with only the repository mounted, so its summaries have to land
# inside the repository rather than in the host temp directory.
results="ops/k6/results/.harness-check"
mkdir -p "$results"
trap 'rm -rf "$work" "$repo_root/$results" "$repo_root/ops/k6/query-set.verify.json"; kill "${stub_pid:-0}" 2>/dev/null || true' EXIT

# Deterministic query set: the same seed must produce the same file.
python3 ops/k6/build-query-set.py --out "$work/query-set.json" --count 200 --seed 7 >/dev/null
python3 ops/k6/build-query-set.py --out "$work/query-set-again.json" --count 200 --seed 7 >/dev/null
cmp -s "$work/query-set.json" "$work/query-set-again.json" \
  || { echo "FAIL: the same seed produced a different query set" >&2; exit 1; }
python3 ops/k6/build-query-set.py --out "$work/query-set-other.json" --count 200 --seed 8 >/dev/null
if cmp -s "$work/query-set.json" "$work/query-set-other.json"; then
  echo "FAIL: different seeds produced identical query sets" >&2
  exit 1
fi
cp "$work/query-set.json" ops/k6/query-set.verify.json

# A free port chosen at run time, like every chaos drill here. A fixed port means two runs
# can never coexist: the second binds 8099, fails with "Address already in use", and reports
# it as the harness failing rather than as a collision. That is not hypothetical -- it
# happened three times in a row while verifying this file, and it matters more now that four
# additional drills run in CI alongside this one.
free_port() { python3 -c "import socket;s=socket.socket();s.bind(('127.0.0.1',0));print(s.getsockname()[1]);s.close()"; }
port="$(free_port)"
cat > "$work/stub.py" <<'STUB'
import http.server, json, socketserver, sys, threading, urllib.parse
class Handler(http.server.BaseHTTPRequestHandler):
    seen = set()
    lock = threading.Lock()
    def log_message(self, *args): pass
    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query).get('q', [''])[0]
        with Handler.lock:
            Handler.seen.add(query)
        product = {"id": "p1", "slug": "fixture-product", "name": "Fixture Product",
                   "price": 100, "currency": "INR", "imageUrls": []}
        if parsed.path == '/api/v1/products':
            payload = {"data": [product], "page": 0, "size": 12, "totalElements": 1}
        else:
            payload = {"items": [product], "total": 1, "page": 0, "size": 20}
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get('Content-Length') or 0)
        if length:
            json.loads(self.rfile.read(length))
        if not self.headers.get('Authorization', '').startswith('Bearer '):
            self.send_error(401)
            return
        if self.path.startswith('/api/v1/cart/'):
            status, payload = 200, {"data": {"items": [{"productId": "p1"}], "revision": 1}}
        elif self.path == '/api/v1/orders':
            status, payload = 201, {"id": 1, "status": "PAID", "items": []}
        else:
            self.send_error(404)
            return
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    # The default backlog of 5 refuses connections under even a modest arrival rate, which
    # would look like an application failure rather than a stub limitation.
    request_queue_size = 512
Server(('0.0.0.0', int(sys.argv[1])), Handler).serve_forever()
STUB
python3 "$work/stub.py" "$port" &
stub_pid=$!
for _ in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:${port}/api/v1/search?q=probe" >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -sf "http://127.0.0.1:${port}/api/v1/search?q=probe" >/dev/null

run_regime() {
  local regime="$1" out="$2" url="$3"
  docker run --rm -i --add-host=host.docker.internal:host-gateway -v "$repo_root:/work" -w /work \
    grafana/k6:latest run \
    -e "BASE_URL=$url" -e "CACHE_REGIME=$regime" -e TARGET_RPS=50 -e BENCHMARK_DURATION=8s \
    -e QUERY_SET=ops/k6/query-set.verify.json -e "SUMMARY_OUT=/work/$out" \
    ops/k6/search-load.js >/dev/null 2>&1 || true
}

run_regime cold "$results/cold.json" "http://host.docker.internal:${port}"
run_regime warm "$results/warm.json" "http://host.docker.internal:${port}"
# Nothing is listening here: the harness must report these as failures, not as a clean run.
run_regime cold "$results/unreachable.json" "http://host.docker.internal:1"

# A fixture traffic model, not the realistic one. This check asks whether the journey script
# executes all four steps and writes a well-formed summary; at the realistic 1.2% conversion
# an eight-second run expects about two checkouts, so asserting that any occurred would fail
# roughly one run in six. See ops/k6/traffic-model.harness.json.
docker run --rm -i --add-host=host.docker.internal:host-gateway -v "$repo_root:/work" -w /work \
  grafana/k6:latest run \
  -e "BASE_URL=http://host.docker.internal:${port}" -e ACCESS_TOKEN=synthetic-fixture-token \
  -e TRAFFIC_MODEL=./traffic-model.harness.json \
  -e USER_ID=synthetic-fixture-user -e TARGET_RPS=20 -e BENCHMARK_DURATION=8s \
  -e "SUMMARY_OUT=/work/$results/journey.json" \
  ops/k6/shopper-journey.js >/dev/null 2>&1 || true

python3 - "$repo_root/$results" <<'PY'
import json, pathlib, sys
results = pathlib.Path(sys.argv[1])

def load(name):
    path = results / name
    assert path.exists(), f"{name} was not written by the harness"
    return json.loads(path.read_text())

cold, warm, unreachable = load('cold.json'), load('warm.json'), load('unreachable.json')
journey = load('journey.json')

assert cold['results']['requests'] > 0, 'cold run issued no requests'
assert cold['results']['distinct_query_requests'] == cold['results']['requests'], \
    'a cold run must issue a distinct query per request'
assert cold['results']['repeat_query_requests'] == 0, 'a cold run repeated a query'
assert warm['results']['repeat_query_requests'] == warm['results']['requests'], \
    'a warm run must repeat its small query set'
assert warm['results']['distinct_query_requests'] == 0, 'a warm run issued distinct queries'
assert cold['results']['failed_rate'] == 0, \
    f"cold run against a healthy stub reported failures: {cold['results']['failed_rate']}"

# The accounting the old hey parser got wrong: requests that never got a response.
assert unreachable['results']['failed_rate'] == 1, \
    f"connection failures were not counted: failed_rate={unreachable['results']['failed_rate']}"

for report in (cold, warm):
    assert report['query_set']['seed'] == 7
    assert 'not shopper traffic' in report['query_set']['provenance']
    assert report['run']['cache_regime'] in ('cold', 'warm')
    assert report['results']['latency_ms']['p95'] is not None
    assert report['limits'], 'the summary must state its limits'

assert journey['evidence_class'] == 'invented_synthetic_shopper_journey'
assert journey['results']['browse_attempts'] > 0
assert journey['results']['search_attempts'] > 0
assert journey['results']['cart_attempts'] > 0
assert journey['results']['checkout_attempts'] > 0
assert journey['results']['step_failed_rate'] == 0
assert 'invented' in journey['limits'][0]

print(f"Verified the load harness: cold={cold['results']['requests']} distinct requests, "
      f"warm={warm['results']['requests']} repeated, unreachable run reported 100% failure, "
      f"journey={journey['results']['checkout_attempts']} synthetic checkouts")
PY
