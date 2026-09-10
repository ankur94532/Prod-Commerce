#!/usr/bin/env bash
# Fault injection against order-service: the money path.
#
# Two failures, and the right answer is opposite in each, which is the point of testing them
# together rather than assuming "dependency down means fail".
#
#   1. PostgreSQL unreachable -> checkout must fail. The dangerous answer is a 2xx carrying an
#      order id for a row that was never written: the shopper is told they bought something,
#      the payment path believes an order exists, and nothing in the database agrees. The
#      drill also checks afterwards that no phantom order appeared.
#
#   2. Kafka unreachable -> checkout must SUCCEED. That is what the outbox is for: the event
#      is written in the same transaction as the order, and published afterwards. If checkout
#      fails when Kafka is down then Kafka is a hard dependency of taking money, which nobody
#      decided and which the outbox exists to prevent. The event must also still be there,
#      unpublished, rather than dropped -- and must drain once Kafka returns.
#
# Disposable containers on random ports. Touches no project volumes or Compose services.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

suffix="$$"
pg="chaos-order-pg-$suffix"
kafka="chaos-order-kafka-$suffix"
work="$(mktemp -d)"
app_pid=""
catalog_pid=""
cleaned=0

cleanup() {
  local status=$?
  [ "$cleaned" = 1 ] && return
  cleaned=1
  set +e
  for pid in "$app_pid" "$catalog_pid"; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null
  done
  docker unpause "$pg" >/dev/null 2>&1
  docker unpause "$kafka" >/dev/null 2>&1
  docker rm -f "$pg" "$kafka" >/dev/null 2>&1
  rm -rf "$work"
  return "$status"
}
trap cleanup EXIT

free_port() { python3 -c "import socket;s=socket.socket();s.bind(('127.0.0.1',0));print(s.getsockname()[1]);s.close()"; }

echo "Starting PostgreSQL and Kafka"
docker run --rm -d --name "$pg" -e POSTGRES_USER=order_service -e POSTGRES_PASSWORD=order_pw \
  -e POSTGRES_DB=ecom_order -p 127.0.0.1::5432 postgres:16 >/dev/null
pg_port="$(docker port "$pg" 5432/tcp | cut -d: -f2)"
for _ in $(seq 1 60); do docker exec "$pg" pg_isready -U order_service >/dev/null 2>&1 && break; sleep 1; done

kafka_port="$(free_port)"
docker run --rm -d --name "$kafka" -p "127.0.0.1:${kafka_port}:9092" \
  -e CLUSTER_ID=DAIQKLbNSSSxLaMmI2okqQ -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS="1@localhost:29093" \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENERS="PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093,EXTERNAL://0.0.0.0:9092" \
  -e KAFKA_ADVERTISED_LISTENERS="PLAINTEXT://localhost:29092,EXTERNAL://127.0.0.1:${kafka_port}" \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP="CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT" \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  confluentinc/cp-kafka:7.6.0 >/dev/null
for _ in $(seq 1 60); do
  docker exec "$kafka" kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1 && break
  sleep 2
done

# A catalog stand-in that serves the agreed contract fixture. order-service snapshots the
# name and price from catalog on every checkout, so something has to answer; serving
# contracts/order-catalog/product-snapshot.json means this drill and the contract tests on
# both sides agree on the same bytes, rather than inventing a third shape here.
catalog_port="$(free_port)"
cat > "$work/catalog_stub.py" <<'STUB'
import http.server, json, socketserver, sys
snapshot = json.load(open(sys.argv[2]))
class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args): pass

    def _drain(self):
        length = int(self.headers.get('Content-Length') or 0)
        if length:
            self.rfile.read(length)

    def do_GET(self):
        product_id = self.path.rstrip('/').rsplit('/', 1)[-1]
        body = json.dumps({**snapshot, 'id': int(product_id) if product_id.isdigit() else snapshot['id']}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    # Checkout reserves stock before it charges, and releases it when compensating. A stub
    # that only answered the snapshot GET sent every order down the compensation path, which
    # looks exactly like a payment failure from the outside.
    def do_POST(self):
        self._drain()
        self.send_response(204)
        self.send_header('Content-Length', '0')
        self.end_headers()

    do_PUT = do_POST
    do_PATCH = do_POST
class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True; daemon_threads = True
Server(('127.0.0.1', int(sys.argv[1])), Handler).serve_forever()
STUB
python3 "$work/catalog_stub.py" "$catalog_port" "$repo_root/contracts/order-catalog/product-snapshot.json" &
catalog_pid=$!

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/jwt.pem" 2>/dev/null
openssl rsa -in "$work/jwt.pem" -pubout -outform DER -out "$work/jwt.pub.der" 2>/dev/null
public_key="$(base64 < "$work/jwt.pub.der" | tr -d '\n')"
token="$(python3 "$repo_root/ops/testing/mint-access-token.py" "$work/jwt.pem" drill-key shopper-1)"

# Same rule as the cart drill: never measure an artifact older than the source it claims to be.
jar="$(ls backend/order-service/target/order-service-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
if [ -n "$jar" ]; then
  if newer="$(find backend/order-service/src/main backend/order-service/pom.xml backend/platform-security/src/main \
        -type f -newer "$jar" -print -quit 2>/dev/null)" && [ -n "$newer" ]; then
    echo "Rebuilding: $jar is older than $newer"
    jar=""
  fi
fi
if [ -z "$jar" ]; then
  mvn -f backend/pom.xml -pl order-service -am -o -q package -DskipTests
  jar="$(ls backend/order-service/target/order-service-*.jar | grep -v sources | head -1)"
fi

app_port="$(free_port)"
echo "Starting order-service"
SERVER_PORT="$app_port" \
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${pg_port}/ecom_order" \
SPRING_DATASOURCE_USERNAME=order_service SPRING_DATASOURCE_PASSWORD=order_pw \
SPRING_FLYWAY_ENABLED=true \
SPRING_KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:${kafka_port}" \
CATALOG_SERVICE_URL="http://127.0.0.1:${catalog_port}" \
SECURITY_JWT_ACTIVE_KEY_ID=drill-key SECURITY_JWT_PUBLIC_KEY_BASE64="$public_key" \
INTERNAL_SERVICE_TOKEN=chaos_order_internal_token_value \
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0 \
  java -jar "$jar" > "$work/app.log" 2>&1 &
app_pid=$!

for _ in $(seq 1 150); do
  curl -sf "http://127.0.0.1:${app_port}/api/v1/orders/health" >/dev/null 2>&1 && break
  kill -0 "$app_pid" 2>/dev/null || { echo "order-service exited during startup" >&2; tail -30 "$work/app.log" >&2; exit 1; }
  sleep 1
done
curl -sf "http://127.0.0.1:${app_port}/api/v1/orders/health" >/dev/null

auth=(-H "Authorization: Bearer ${token}" -H 'Content-Type: application/json')
psql_order() { docker exec -e PGPASSWORD=order_pw "$pg" psql -U order_service -d ecom_order -tAc "$1" 2>/dev/null | tr -d '\r'; }
psql_admin() { docker exec -e PGPASSWORD=order_pw "$pg" psql -U order_service -d postgres -v ON_ERROR_STOP=1 -tAc "$1" 2>/dev/null | tr -d '\r'; }

# Prints "<status> <order id or ->"
place_order() {
  local key="$1" code
  code="$(curl -s -o "$work/order.json" -w '%{http_code}' -X POST "http://127.0.0.1:${app_port}/api/v1/orders" \
    "${auth[@]}" -H "Idempotency-Key: ${key}" \
    -d '{"items":[{"productId":"42","quantity":1}],"payment":{"paymentToken":"pm_chaosdrill123456"}}')"
  local id
  id="$(python3 -c "
import json
try:
    print(json.load(open('$work/order.json')).get('id') or '-')
except Exception:
    print('-')
")"
  echo "$code $id"
}

checks=0

echo "1. Baseline: an order completes and is persisted as PAID"
read -r code order_id <<< "$(place_order chaos-order-0001)"
# 201 specifically, not any 2xx. The controller answers 202 for PENDING_PAYMENT and
# COMPENSATING, so a checkout that fell down the compensation path -- because the catalog
# stub refused a stock reservation, say -- would still look like success to a lenient check,
# and every later assertion about the outbox would then be testing an order that was never
# completed. This drill learned that the hard way.
[ "$code" = "201" ] || {
  echo "FAIL: baseline checkout returned ${code}, expected 201 (PAID)." >&2
  echo "      202 means the order stopped at PENDING_PAYMENT or COMPENSATING." >&2
  echo "      body: $(cat "$work/order.json")" >&2
  grep -iE "compensat|error|exception" "$work/app.log" | tail -8 >&2
  exit 1; }
paid="$(psql_order "SELECT count(*) FROM orders WHERE status = 'PAID';")"
[ "${paid:-0}" -ge 1 ] || {
  echo "FAIL: checkout answered 201 but no PAID order row exists" >&2; exit 1; }
checks=$((checks + 1))
echo "   201, order ${order_id} persisted as PAID"

before_orders="$(psql_order "SELECT count(*) FROM orders;")"

echo "2. PostgreSQL paused: checkout must fail, never confirm an order it cannot store"
docker pause "$pg" >/dev/null
sleep 1
read -r code order_id <<< "$(place_order chaos-order-0002)"
case "$code" in
  200|201|202)
    echo "FAIL: checkout answered ${code} while PostgreSQL was unreachable." >&2
    echo "      The shopper is told the order exists; the database has never heard of it." >&2
    docker unpause "$pg" >/dev/null 2>&1 || true
    exit 1 ;;
esac
docker unpause "$pg" >/dev/null
for _ in $(seq 1 30); do docker exec "$pg" pg_isready -U order_service >/dev/null 2>&1 && break; sleep 1; done
checks=$((checks + 1))
echo "   answered ${code} rather than confirming an unstorable order"

echo "3. And no phantom order was left behind"
after_orders="$(psql_order "SELECT count(*) FROM orders;")"
[ "$after_orders" = "$before_orders" ] || {
  echo "FAIL: order count went from ${before_orders} to ${after_orders} across a failed checkout" >&2
  exit 1; }
checks=$((checks + 1))
echo "   still ${after_orders} order(s); the failed attempt persisted nothing"

echo "4. PostgreSQL read-only: it still accepts connections and reads, but checkout fails"
# Unlike pausing the container, this preserves a live database endpoint. Force the pool to
# reconnect after changing the database default so every subsequent transaction observes
# read-only mode, as it would after being pointed at a read replica.
psql_admin "ALTER DATABASE ecom_order SET default_transaction_read_only = on;" >/dev/null
psql_admin "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'ecom_order';" >/dev/null
read_probe="$(psql_order 'SELECT count(*) FROM orders;')"
[ "$read_probe" = "$after_orders" ] || {
  echo "FAIL: the read-only database did not remain readable" >&2; exit 1; }
read -r code order_id <<< "$(place_order chaos-order-0003)"
case "$code" in
  200|201|202)
    echo "FAIL: checkout answered ${code} while PostgreSQL was read-only." >&2
    echo "      The database accepted connections but could not persist the order." >&2
    exit 1 ;;
esac
checks=$((checks + 1))
echo "   reads still worked; checkout answered ${code}"

echo "5. The read-only write failure left no phantom order"
after_readonly="$(psql_order 'SELECT count(*) FROM orders;')"
[ "$after_readonly" = "$after_orders" ] || {
  echo "FAIL: order count went from ${after_orders} to ${after_readonly} across a read-only failure" >&2
  exit 1; }
checks=$((checks + 1))
echo "   still ${after_readonly} order(s)"

echo "6. Writable primary restored: checkout recovers without an application restart"
psql_admin "ALTER DATABASE ecom_order RESET default_transaction_read_only;" >/dev/null
psql_admin "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'ecom_order';" >/dev/null
[ "$(psql_order 'SHOW transaction_read_only;')" = off ] || {
  echo "FAIL: PostgreSQL still reports read-only after restoring the primary" >&2; exit 1; }
recovered=""
for _ in $(seq 1 20); do
  read -r code order_id <<< "$(place_order chaos-order-0004)"
  if [ "$code" = 201 ]; then recovered=yes; break; fi
  sleep 1
done
[ -n "$recovered" ] || {
  echo "FAIL: checkout returned ${code} after restoring writes" >&2
  echo "      body: $(cat "$work/order.json")" >&2
  exit 1; }
checks=$((checks + 1))
echo "   201, order ${order_id} persisted after write access returned"

echo "7. Kafka paused: checkout must still succeed, because that is what the outbox is for"
docker pause "$kafka" >/dev/null
sleep 1
read -r code order_id <<< "$(place_order chaos-order-0005)"
case "$code" in
  201) ;;
  *) echo "FAIL: checkout returned ${code} while only Kafka was unreachable." >&2
     echo "      The outbox exists precisely so the broker is not a hard dependency of" >&2
     echo "      taking money: the event is written in the order's own transaction and" >&2
     echo "      published afterwards." >&2
     echo "      body: $(cat "$work/order.json")" >&2
     docker unpause "$kafka" >/dev/null 2>&1 || true
     exit 1 ;;
esac
checks=$((checks + 1))
echo "   201: the order completed with the broker down"

echo "8. The event was kept, unpublished, rather than dropped"
unpublished="$(psql_order "SELECT count(*) FROM outbox_events WHERE published_at IS NULL;")"
[ "${unpublished:-0}" -ge 1 ] || {
  echo "FAIL: no unpublished outbox row exists after a checkout with Kafka down." >&2
  echo "      The order was accepted and its event went nowhere." >&2
  docker unpause "$kafka" >/dev/null 2>&1 || true
  exit 1; }
checks=$((checks + 1))
echo "   ${unpublished} event(s) waiting in the outbox"

echo "9. Kafka back: the outbox drains without a restart"
docker unpause "$kafka" >/dev/null
drained=""
for _ in $(seq 1 40); do
  remaining="$(psql_order "SELECT count(*) FROM outbox_events WHERE published_at IS NULL;")"
  if [ "${remaining:-1}" = "0" ]; then drained="yes"; break; fi
  sleep 3
done
[ -n "$drained" ] || {
  echo "FAIL: the outbox still holds unpublished events after Kafka returned." >&2
  echo "      An event that is never published is lost, just more slowly." >&2
  exit 1; }
checks=$((checks + 1))
echo "   outbox drained"

echo
echo "Order chaos drill passed ${checks} checks: a database outage fails checkout instead of"
echo "confirming an order it cannot store and leaves nothing behind, while a broker outage does"
echo "not stop checkout and loses no event."
