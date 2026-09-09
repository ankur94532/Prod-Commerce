#!/usr/bin/env bash
# Stands up catalog-service and search-service over disposable PostgreSQL, Elasticsearch and
# Redis, seeds a catalog, builds the index, and runs the graded-evaluation pipeline as far as
# it can go without judgements: snapshot -> collect -> pool.
#
# What comes out is a frozen run and a blinded pool of query/product pairs. Grading them is a
# separate, deliberate act: see backend/search-service/evaluation/RUBRIC.md. Nothing here
# produces a relevance number.
#
# The catalog is generated, not a real assortment, and the queries are AI-authored. Both
# limits travel with the run in its manifest.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

OUT="${1:-backend/search-service/evaluation/runs/$(date -u +%Y%m%dT%H%M%SZ)}"
SEED_SIZE="${SEED_SIZE:-600}"
DEPTH="${DEPTH:-10}"

suffix="$$"
pg="eval-pg-$suffix"; es="eval-es-$suffix"; redis="eval-redis-$suffix"
work="$(mktemp -d)"
catalog_pid=""; search_pid=""; stub_pid=""

# Keep the service logs when something fails; the failure is usually only visible there.
keep_logs() {
  local dest="${OUT}-logs"
  mkdir -p "$dest" 2>/dev/null || return 0
  cp "$work"/*.log "$dest"/ 2>/dev/null || true
  echo "Service logs kept in $dest" >&2
}

cleanup() {
  [ "${succeeded:-0}" = "1" ] || keep_logs
  for pid in "$search_pid" "$catalog_pid" "$stub_pid"; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null && wait "$pid" 2>/dev/null
  done
  docker rm -f "$pg" "$es" "$redis" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

free_port() { python3 -c "import socket;s=socket.socket();s.bind(('127.0.0.1',0));print(s.getsockname()[1]);s.close()"; }

echo "Starting PostgreSQL, Elasticsearch and Redis"
docker run --rm -d --name "$pg" -e POSTGRES_USER=catalog_service -e POSTGRES_PASSWORD=catalog_pw \
  -e POSTGRES_DB=ecom_catalog -p 127.0.0.1::5432 postgres:16 >/dev/null
docker run --rm -d --name "$es" -e discovery.type=single-node -e xpack.security.enabled=false \
  -e "ES_JAVA_OPTS=-Xms1g -Xmx1g" -p 127.0.0.1::9200 \
  docker.elastic.co/elasticsearch/elasticsearch:8.15.2 >/dev/null
docker run --rm -d --name "$redis" -p 127.0.0.1::6379 redis:7 >/dev/null

pg_port="$(docker port "$pg" 5432/tcp | cut -d: -f2)"
es_port="$(docker port "$es" 9200/tcp | cut -d: -f2)"
redis_port="$(docker port "$redis" 6379/tcp | cut -d: -f2)"

for _ in $(seq 1 90); do docker exec "$pg" pg_isready -U catalog_service >/dev/null 2>&1 && break; sleep 1; done
for _ in $(seq 1 120); do
  curl -sf "http://127.0.0.1:${es_port}/_cluster/health?wait_for_status=yellow&timeout=1s" >/dev/null 2>&1 && break
  sleep 1
done

cat > "$work/embedding_stub.py" <<'STUB'
import hashlib, http.server, json, math, socketserver, sys
DIMENSIONS = 384
def embed(text):
    # Deterministic bag-of-words hashing, the same idea as the in-process fallback: similar
    # wording lands in similar directions. It is not a trained model and makes no semantic
    # claim; it exists so the vector path is exercised end to end.
    vector = [0.0] * DIMENSIONS
    for token in (text or '').lower().split():
        digest = hashlib.sha256(token.encode()).digest()
        index = int.from_bytes(digest[:4], 'big') % DIMENSIONS
        vector[index] += 1.0
    norm = math.sqrt(sum(v * v for v in vector)) or 1.0
    return [v / norm for v in vector]
class Handler(http.server.BaseHTTPRequestHandler):
    # HTTP/1.0 closes the connection after each response, which the client sees as a write
    # failure part-way through a batch. Keep-alive with an explicit Content-Length is what
    # a real service would do.
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args): pass
    def _send(self, payload):
        body = json.dumps(payload).encode()
        self.send_response(200); self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        self._send({"status": "ok", "model": "hashing-stub", "dimensions": DIMENSIONS})
    def do_POST(self):
        try:
            # The client streams the batch with chunked transfer encoding, so there is no
            # Content-Length to read. Reading zero bytes made every batch look like an empty
            # request, which the stub then answered with a single embedding -- and the client
            # correctly rejected the mismatched response.
            raw = b''
            if 'chunked' in (self.headers.get('Transfer-Encoding') or '').lower():
                while True:
                    size_line = self.rfile.readline().strip()
                    if not size_line:
                        break
                    size = int(size_line.split(b';')[0], 16)
                    if size == 0:
                        self.rfile.readline()
                        break
                    while size > 0:
                        chunk = self.rfile.read(size)
                        if not chunk:
                            break
                        raw += chunk
                        size -= len(chunk)
                    self.rfile.readline()
            else:
                length = int(self.headers.get('Content-Length', 0))
                while len(raw) < length:
                    chunk = self.rfile.read(length - len(raw))
                    if not chunk:
                        break
                    raw += chunk
            payload = json.loads(raw or b'{}')
            texts = payload.get('texts') or [payload.get('text', '')]
            vectors = [embed(t) for t in texts]
            with open(sys.argv[2], 'a') as audit:
                audit.write(f"asked={len(texts)} returned={len(vectors)} dims={len(vectors[0]) if vectors else 0}\n")
            self._send({"embeddings": vectors, "embedding": vectors[0],
                        "model": "hashing-stub", "dimensions": DIMENSIONS})
        except Exception as error:            # answer, rather than dropping the connection
            body = json.dumps({"error": str(error)}).encode()
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True; daemon_threads = True; request_queue_size = 256
Server(('127.0.0.1', int(sys.argv[1])), Handler).serve_forever()
STUB
embedding_port="$(free_port)"
python3 "$work/embedding_stub.py" "$embedding_port" "$work/embedding.log" &
stub_pid=$!

catalog_jar="$(ls backend/catalog-service/target/catalog-service-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
search_jar="$(ls backend/search-service/target/search-service-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
if [ -z "$catalog_jar" ] || [ -z "$search_jar" ]; then
  mvn -f backend/pom.xml -pl catalog-service,search-service -am -o -q package -DskipTests
  catalog_jar="$(ls backend/catalog-service/target/catalog-service-*.jar | grep -v sources | head -1)"
  search_jar="$(ls backend/search-service/target/search-service-*.jar | grep -v sources | head -1)"
fi

export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${pg_port}/ecom_catalog"
export SPRING_DATASOURCE_USERNAME=catalog_service
export SPRING_DATASOURCE_PASSWORD=catalog_pw
export SECURITY_JWT_SECRET=evaluation_run_secret_of_at_least_32_chars
export INTERNAL_SERVICE_TOKEN=evaluation_run_internal_token_value
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0
export SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:1

echo "Applying the catalog schema"
SPRING_FLYWAY_ENABLED=true java -jar "$catalog_jar" --spring.profiles.active=migrate \
  > "$work/migrate.log" 2>&1

catalog_port="$(free_port)"
search_port="$(free_port)"

echo "Seeding ${SEED_SIZE} products"
SPRING_FLYWAY_ENABLED=false CATALOG_SEED_SIZE="$SEED_SIZE" \
  GOCOMMERCE_SEARCH_BASE_URL="http://127.0.0.1:${search_port}" \
  java -jar "$catalog_jar" --spring.profiles.active=seed --spring.main.web-application-type=none \
  > "$work/seed.log" 2>&1 || { echo "seeding failed" >&2; tail -25 "$work/seed.log" >&2; exit 1; }
seeded="$(grep -oE 'Seeded or updated [0-9]+' "$work/seed.log" | grep -oE '[0-9]+' | tail -1 || true)"
[ "${seeded:-0}" -gt 0 ] || {
  echo "FAIL: the seed job reported no products written" >&2; tail -25 "$work/seed.log" >&2; exit 1; }
echo "   seeded ${seeded} products"

echo "Starting catalog-service on ${catalog_port}"
SPRING_FLYWAY_ENABLED=false SERVER_PORT="$catalog_port" \
  GOCOMMERCE_SEARCH_BASE_URL="http://127.0.0.1:${search_port}" \
  java -jar "$catalog_jar" > "$work/catalog.log" 2>&1 &
catalog_pid=$!
for _ in $(seq 1 120); do
  curl -sf "http://127.0.0.1:${catalog_port}/api/v1/products?page=0&size=1" >/dev/null 2>&1 && break
  kill -0 "$catalog_pid" 2>/dev/null || { echo "catalog-service exited" >&2; tail -25 "$work/catalog.log" >&2; exit 1; }
  sleep 1
done

# The catalog client's circuit breaker falls back to an empty page, so a catalog that is
# unreachable is indistinguishable from one that is empty. Check before indexing.
catalog_total="$(curl -sf "http://127.0.0.1:${catalog_port}/api/v1/products?page=0&size=1" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("totalElements", 0))')"
[ "${catalog_total:-0}" -gt 0 ] || {
  echo "FAIL: catalog-service is serving ${catalog_total} products" >&2; tail -25 "$work/catalog.log" >&2; exit 1; }
echo "   catalog is serving ${catalog_total} products"

echo "Starting search-service on ${search_port}"
SERVER_PORT="$search_port" \
SPRING_ELASTICSEARCH_URIS="http://127.0.0.1:${es_port}" \
SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT="$redis_port" \
EMBEDDING_SERVICE_BASE_URL="http://127.0.0.1:${embedding_port}" \
SEARCH_INDEXING_BATCH_SIZE=32 SEARCH_HTTP_READ_TIMEOUT=30s SEARCH_HTTP_CONNECT_TIMEOUT=5s \
CATALOG_SERVICE_URL="http://127.0.0.1:${catalog_port}" \
CATALOG_BASE_URL="http://127.0.0.1:${catalog_port}" \
RECOMMENDATION_BASE_URL="http://127.0.0.1:1" \
  java -jar "$search_jar" --search.bootstrap.reindex-empty-on-startup=false \
  > "$work/search.log" 2>&1 &
search_pid=$!
for _ in $(seq 1 150); do
  curl -sf "http://127.0.0.1:${search_port}/api/v1/search/health" >/dev/null 2>&1 && break
  kill -0 "$search_pid" 2>/dev/null || { echo "search-service exited" >&2; tail -25 "$work/search.log" >&2; exit 1; }
  sleep 1
done

echo "Building the index"
indexed="$(curl -sf -X POST "http://127.0.0.1:${search_port}/api/v1/search/reindex" \
  -H "X-Internal-Service-Token: ${INTERNAL_SERVICE_TOKEN}")"
echo "   $indexed"
python3 -c "
import json,sys
r = json.loads('''$indexed''')
if not r.get('consistent'):
    sys.exit('reindex was inconsistent: ' + json.dumps(r))
if r.get('indexed', 0) < 1:
    sys.exit('nothing was indexed')
"

mkdir -p "$(dirname "$OUT")"
evaluation=backend/search-service/evaluation

cat > "$work/config.json" <<CONFIG
{
  "index_identity": "disposable elasticsearch:8.15.2, single node, index rebuilt for this run",
  "embedding_model": "hashing-stub (deterministic bag-of-words, not a trained model)",
  "embedding_revision": "ops/evaluation/collect-run.sh embedded stub",
  "cache_regime": "redis started empty for this run; each query issued once per mode",
  "corpus_provenance": "generated seed catalog of ${SEED_SIZE} products, not a real assortment"
}
CONFIG

echo "Snapshotting the catalog"
python3 "$evaluation/graded_eval.py" snapshot \
  --base-url "http://127.0.0.1:${catalog_port}" --out "$work/catalog.jsonl" \
  --provenance "generated_seed_catalog_${SEED_SIZE}"

echo "Collecting every query against every mode"
python3 "$evaluation/graded_eval.py" collect \
  --base-url "http://127.0.0.1:${search_port}" \
  --queries "$evaluation/queries.v1.json" \
  --catalog "$work/catalog.jsonl" \
  --config "$work/config.json" \
  --out "$OUT" --depth "$DEPTH"

echo "Exporting a blinded pool"
python3 "$evaluation/graded_eval.py" pool --run "$OUT" --out "$OUT/pool"

python3 - "$OUT" <<'PY'
import json, pathlib, sys, collections
run = pathlib.Path(sys.argv[1])
pool = json.loads((run / 'pool' / 'pool.json').read_text())
queries = {q['query_id']: q for q in json.loads((run / 'queries.json').read_text())['queries']}
by_split = collections.Counter(queries[p['query_id']]['split'] for p in pool['pairs'])
print(f"\nPool: {len(pool['pairs'])} query/product pairs to judge "
      f"(dev {by_split['dev']}, test {by_split['test']})")
print(f"Run:  {run}")
print("Nothing here is a relevance number yet. Grade the pool per RUBRIC.md, then run"
      " graded_eval.py evaluate.")
PY
succeeded=1
