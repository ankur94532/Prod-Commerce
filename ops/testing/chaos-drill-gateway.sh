#!/usr/bin/env bash
# Verifies the gateway rate limiter's deliberately fail-open behavior when Redis stalls.
#
# Redis here stores only rate-limit counters; it is not the cart system of record. Keeping
# product reads available is the chosen tradeoff, at the cost of temporarily losing abuse
# and cost protection. The upstream is a deterministic stub so this drill measures the
# gateway limiter timeout rather than search-service's separate Redis cache timeout.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

suffix="$$"
redis="chaos-gateway-redis-$suffix"
work="$(mktemp -d)"
gateway_pid=""
stub_pid=""
cleaned=0

cleanup() {
  local status=$?
  [ "$cleaned" = 1 ] && return
  cleaned=1
  set +e
  [ -n "$gateway_pid" ] && kill "$gateway_pid" 2>/dev/null
  [ -n "$stub_pid" ] && kill "$stub_pid" 2>/dev/null
  docker unpause "$redis" >/dev/null 2>&1
  docker rm -f "$redis" >/dev/null 2>&1
  rm -rf "$work"
  return "$status"
}
trap cleanup EXIT

free_port() { python3 -c "import socket;s=socket.socket();s.bind(('127.0.0.1',0));print(s.getsockname()[1]);s.close()"; }

docker run --rm -d --name "$redis" -p 127.0.0.1::6379 redis:7 >/dev/null
redis_port="$(docker port "$redis" 6379/tcp | cut -d: -f2)"
for _ in $(seq 1 30); do
  docker exec "$redis" redis-cli ping >/dev/null 2>&1 && break
  sleep 1
done

stub_port="$(free_port)"
cat > "$work/search_stub.py" <<'PY'
import http.server, json, socketserver, sys

class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args): pass
    def do_GET(self):
        body = json.dumps({
            'items': [{'id': '1', 'slug': 'limiter-probe', 'name': 'Limiter Probe'}],
            'total': 1, 'page': 0, 'size': 5,
        }).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    request_queue_size = 128

Server(('127.0.0.1', int(sys.argv[1])), Handler).serve_forever()
PY
python3 "$work/search_stub.py" "$stub_port" &
stub_pid=$!

jar="$(ls backend/api-gateway/target/api-gateway-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
if [ -n "$jar" ]; then
  newer="$(find backend/api-gateway/src/main backend/api-gateway/pom.xml backend/platform-security/src/main \
    -type f -newer "$jar" -print -quit 2>/dev/null || true)"
  [ -z "$newer" ] || jar=""
fi
if [ -z "$jar" ]; then
  mvn -f backend/pom.xml -pl api-gateway -am -o -q package -DskipTests
  jar="$(ls backend/api-gateway/target/api-gateway-*.jar | grep -v sources | head -1)"
fi
unzip -p "$jar" BOOT-INF/classes/application.yml 2>/dev/null \
  | grep -q 'timeout: ${SPRING_DATA_REDIS_TIMEOUT:1s}' || {
    echo "FAIL: packaged gateway does not declare the one-second Redis timeout" >&2
    exit 1
  }

gateway_port="$(free_port)"
management_port="$(free_port)"
DEBUG=false LOGGING_LEVEL_ROOT=INFO SERVER_PORT="$gateway_port" MANAGEMENT_SERVER_PORT="$management_port" \
SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT="$redis_port" \
SEARCH_SERVICE_URI="http://127.0.0.1:${stub_port}" \
SECURITY_JWT_SECRET=chaos_gateway_secret_of_at_least_32_chars \
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0 \
  java -jar "$jar" > "$work/gateway.log" 2>&1 &
gateway_pid=$!
for _ in $(seq 1 120); do
  curl -sf "http://127.0.0.1:${management_port}/actuator/health" >/dev/null 2>&1 && break
  kill -0 "$gateway_pid" 2>/dev/null || {
    echo "gateway exited during startup" >&2; tail -40 "$work/gateway.log" >&2; exit 1; }
  sleep 1
done

url="http://127.0.0.1:${gateway_port}/api/v1/search?q=probe&page=0&size=5"
read_response() {
  local output status seconds items
  output="$(curl --max-time 15 -sS -o "$work/body.json" -w '%{http_code} %{time_total}' "$url")"
  status="${output%% *}"
  seconds="${output#* }"
  items="$(python3 -c "import json;print(len(json.load(open('$work/body.json')).get('items', [])))")"
  echo "$status $seconds $items"
}

checks=0
echo "1. Baseline reaches the upstream through an active limiter"
read -r status seconds items <<< "$(read_response)"
[ "$status" = 200 ] && [ "$items" = 1 ] || {
  echo "FAIL: baseline returned status=$status items=$items" >&2; exit 1; }
checks=$((checks + 1))

echo "2. Redis stalled: limiter fails open and preserves the non-empty response"
docker pause "$redis" >/dev/null
sleep 1
slowest=0
for _ in $(seq 1 3); do
  read -r status seconds items <<< "$(read_response)"
  elapsed_ms="$(python3 -c "print(round(float('$seconds') * 1000))")"
  [ "$elapsed_ms" -gt "$slowest" ] && slowest="$elapsed_ms"
  [ "$status" = 200 ] && [ "$items" = 1 ] || {
    echo "FAIL: fail-open decision changed: status=$status items=$items" >&2
    docker unpause "$redis" >/dev/null 2>&1 || true
    exit 1
  }
done
checks=$((checks + 1))
echo "   three 200 responses retained the upstream item"

echo "3. The stalled limiter is bounded well below Lettuce's 60-second default"
budget_ms="${CHAOS_GATEWAY_BUDGET_MS:-5000}"
[ "$slowest" -le "$budget_ms" ] || {
  echo "FAIL: slowest request was ${slowest}ms (budget ${budget_ms}ms)" >&2
  docker unpause "$redis" >/dev/null 2>&1 || true
  exit 1
}
checks=$((checks + 1))
echo "   slowest request was ${slowest}ms"

echo "4. Redis recovery restores rate enforcement without restarting the gateway"
docker unpause "$redis" >/dev/null
sleep 2
limited=0
# The search route deliberately allows a burst of 100 and replenishes 50/second, so a
# five-request probe cannot prove anything. Exceed that configured burst substantially.
for _ in $(seq 1 400); do
  status="$(curl -sS -o /dev/null -w '%{http_code}' "$url")"
  if [ "$status" = 429 ]; then limited=1; break; fi
done
[ "$limited" = 1 ] || { echo "FAIL: no request was rate limited after Redis recovered" >&2; exit 1; }
checks=$((checks + 1))

echo
echo "Gateway Redis chaos drill passed ${checks} checks. Decision: fail open for storefront"
echo "reads; accept that abuse protection is absent until Redis recovers."
