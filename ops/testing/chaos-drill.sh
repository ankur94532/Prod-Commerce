#!/usr/bin/env bash
# Fault injection against a running search service. Unit tests assert how the code handles a
# dependency that throws; this asserts what actually happens when the dependency stops
# answering mid-flight, which is the failure shape production sees.
#
# Two properties, both of which have a wrong answer that looks healthy:
#
#   1. Elasticsearch down  -> search must fail loudly (503). Returning 200 with zero results
#      is the dangerous answer: fast, "successful", and indistinguishable from a catalog
#      that genuinely has nothing matching.
#   2. Redis down          -> search must keep working. The cache is an optimization; if a
#      cache outage takes search down, the cache is a dependency and nobody decided that.
#
# Disposable containers on random ports. Touches no project volumes or Compose services.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

suffix="$$"
es="chaos-es-$suffix"
redis="chaos-redis-$suffix"
work="$(mktemp -d)"
app_pid=""

cleanup() {
  [ -n "$app_pid" ] && kill "$app_pid" 2>/dev/null && wait "$app_pid" 2>/dev/null
  [ -n "${stub_pid:-}" ] && kill "$stub_pid" 2>/dev/null
  docker rm -f "$es" "$redis" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

echo "Starting Elasticsearch and Redis"
docker run --rm -d --name "$es" -e discovery.type=single-node -e xpack.security.enabled=false \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" -p 127.0.0.1::9200 \
  docker.elastic.co/elasticsearch/elasticsearch:8.15.2 >/dev/null
docker run --rm -d --name "$redis" -p 127.0.0.1::6379 redis:7 >/dev/null

es_port="$(docker port "$es" 9200/tcp | cut -d: -f2)"
redis_port="$(docker port "$redis" 6379/tcp | cut -d: -f2)"
for _ in $(seq 1 90); do
  curl -sf "http://127.0.0.1:${es_port}/_cluster/health?wait_for_status=yellow&timeout=1s" >/dev/null 2>&1 && break
  sleep 1
done
curl -sf "http://127.0.0.1:${es_port}/_cluster/health?wait_for_status=yellow&timeout=1s" >/dev/null

# A stand-in for the embedding service: deterministic vectors, no model download.
cat > "$work/embedding_stub.py" <<'STUB'
import http.server, json, socketserver, sys
DIMENSIONS = 384
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def _send(self, payload):
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def do_GET(self):
        self._send({"status": "ok", "model": "chaos-stub"})
    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        payload = json.loads(self.rfile.read(length) or b'{}')
        texts = payload.get('texts') or [payload.get('text', '')]
        vector = [0.01] * DIMENSIONS
        self._send({"embeddings": [vector for _ in texts], "embedding": vector,
                    "model": "chaos-stub", "dimensions": DIMENSIONS})
class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    request_queue_size = 256
Server(('127.0.0.1', int(sys.argv[1])), Handler).serve_forever()
STUB
# Free ports chosen at run time: a fixed port silently reuses a stub left by an aborted
# run, which makes the drill test the wrong process.
free_port() { python3 -c "import socket;s=socket.socket();s.bind(('127.0.0.1',0));print(s.getsockname()[1]);s.close()"; }
embedding_port="$(free_port)"
python3 "$work/embedding_stub.py" "$embedding_port" &
stub_pid=$!
for _ in $(seq 1 20); do
  curl -sf "http://127.0.0.1:${embedding_port}/health" >/dev/null 2>&1 && break
  sleep 1
done

jar="$(ls backend/search-service/target/search-service-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
if [ -z "$jar" ]; then
  mvn -f backend/pom.xml -pl search-service -am -o -q package -DskipTests
  jar="$(ls backend/search-service/target/search-service-*.jar | grep -v sources | head -1 || true)"
fi

app_port="$(free_port)"
echo "Starting search-service"
SERVER_PORT="$app_port" \
SPRING_ELASTICSEARCH_URIS="http://127.0.0.1:${es_port}" \
SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT="$redis_port" \
EMBEDDING_SERVICE_BASE_URL="http://127.0.0.1:${embedding_port}" \
SECURITY_JWT_SECRET=chaos_drill_secret_of_at_least_32_characters \
INTERNAL_SERVICE_TOKEN=chaos_drill_internal_token_value \
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0 \
SEARCH_BOOTSTRAP_REINDEX_EMPTY_ON_STARTUP=false \
java -jar "$jar" --search.bootstrap.reindex-empty-on-startup=false \
  > "$work/app.log" 2>&1 &
app_pid=$!

for _ in $(seq 1 120); do
  curl -sf "http://127.0.0.1:${app_port}/api/v1/search/health" >/dev/null 2>&1 && break
  kill -0 "$app_pid" 2>/dev/null || { echo "search-service exited during startup" >&2; tail -30 "$work/app.log" >&2; exit 1; }
  sleep 1
done
curl -sf "http://127.0.0.1:${app_port}/api/v1/search/health" >/dev/null

# One document behind the alias the service created at startup.
curl -sf -X POST "http://127.0.0.1:${es_port}/products/_doc/1?refresh=true" \
  -H 'Content-Type: application/json' \
  -d '{"productId":1,"slug":"chaos-probe","name":"chaos probe headphones","description":"fixture",
       "brand":"acme","categorySlug":"earbuds-headphones","price":10,"currency":"INR",
       "stockQuantity":5,"searchText":"chaos probe headphones"}' >/dev/null

status_and_items() {
  local out
  out="$(curl -s -o "$work/body.json" -w '%{http_code}' \
    "http://127.0.0.1:${app_port}/api/v1/search?q=chaos&mode=text&page=0&size=10")"
  local items
  items="$(python3 -c "
import json,sys
try:
    print(len(json.load(open('$work/body.json')).get('items') or []))
except Exception:
    print(-1)
")"
  echo "$out $items"
}

flush_cache() { docker exec "$redis" redis-cli FLUSHALL >/dev/null 2>&1 || true; }

checks=0
echo "1. Baseline: search returns results"
read -r code items <<< "$(status_and_items)"
[ "$code" = "200" ] && [ "$items" -ge 1 ] || {
  echo "FAIL: baseline search returned ${code} with ${items} items" >&2; tail -20 "$work/app.log" >&2; exit 1; }
checks=$((checks + 1))
echo "   200 with ${items} item(s)"

echo "2. Elasticsearch paused: search must fail loudly, not return an empty page"
# The cache is flushed first on purpose. A repeated query keeps being served from Redis
# while Elasticsearch is down, which is graceful degradation working as intended -- but it
# means the request never reaches Elasticsearch, so it tests nothing about this failure.
flush_cache
docker pause "$es" >/dev/null
sleep 2
degraded=""
for _ in $(seq 1 20); do
  read -r code items <<< "$(status_and_items)"
  if [ "$code" = "200" ] && [ "$items" = "0" ]; then
    echo "FAIL: search answered 200 with zero results while Elasticsearch was down." >&2
    echo "      That is indistinguishable from a catalog with nothing matching." >&2
    docker unpause "$es" >/dev/null 2>&1 || true
    exit 1
  fi
  if [ "$code" != "200" ]; then degraded="$code"; break; fi
  sleep 2
done
docker unpause "$es" >/dev/null
[ -n "$degraded" ] || { echo "FAIL: search never reported a failure while Elasticsearch was down" >&2; exit 1; }
checks=$((checks + 1))
echo "   answered ${degraded} rather than a false empty result"

echo "3. Elasticsearch back: search recovers without a restart"
flush_cache
recovered=""
for _ in $(seq 1 60); do
  read -r code items <<< "$(status_and_items)"
  if [ "$code" = "200" ] && [ "$items" -ge 1 ]; then recovered="yes"; break; fi
  sleep 2
done
[ -n "$recovered" ] || { echo "FAIL: search did not recover after Elasticsearch returned" >&2; exit 1; }
checks=$((checks + 1))
echo "   recovered"

echo "4. Redis paused: the cache is an optimization, not a dependency"
flush_cache
docker pause "$redis" >/dev/null
survived=""
for _ in $(seq 1 10); do
  read -r code items <<< "$(status_and_items)"
  if [ "$code" = "200" ] && [ "$items" -ge 1 ]; then survived="yes"; break; fi
  sleep 2
done
docker unpause "$redis" >/dev/null
[ -n "$survived" ] || {
  echo "FAIL: search stopped working while only Redis was down." >&2
  echo "      A cache outage must degrade latency, not availability." >&2
  exit 1; }
checks=$((checks + 1))
echo "   still served results"

echo "Chaos drill passed ${checks} checks: a dependency outage fails loudly, recovers on its"
echo "own, and a cache outage does not take search down."
