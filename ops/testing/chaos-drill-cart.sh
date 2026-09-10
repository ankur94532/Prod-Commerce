#!/usr/bin/env bash
# Fault injection against cart-service, where Redis is the system of record.
#
# The search drill asserts that a Redis outage costs latency and not availability, because
# there the cache is an optimization. Cart is the opposite case and the more dangerous one:
# Redis holds the cart itself, so the question is not whether the service survives but
# whether it tells the truth when it cannot read.
#
# The wrong answer looks completely healthy. CartService.getCart does
# findById(...).orElseGet(() -> new Cart(userId)) -- a miss legitimately means "no cart yet"
# -- and if a connection failure were ever caught and folded into that same path, a shopper
# whose cart is intact would be shown an empty one, with 200 OK, no error, and nothing in any
# dashboard. They would re-add everything, or leave. That is the failure this drill exists to
# make impossible to introduce quietly.
#
# Two properties:
#   1. Redis unreachable -> a read must fail loudly. Never 200 with an empty cart.
#   2. It must fail quickly. Lettuce defaults to a 60-second command timeout, and a paused
#      Redis (socket open, nothing answering) is what an overloaded instance looks like.
#
# Disposable containers on random ports. Touches no project volumes or Compose services.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo_root"

suffix="$$"
redis="chaos-cart-redis-$suffix"
work="$(mktemp -d)"
app_pid=""
cleaned=0

cleanup() {
  local status=$?
  [ "$cleaned" = 1 ] && return
  cleaned=1
  set +e
  [ -n "$app_pid" ] && kill "$app_pid" 2>/dev/null
  docker unpause "$redis" >/dev/null 2>&1
  docker rm -f "$redis" >/dev/null 2>&1
  rm -rf "$work"
  return "$status"
}
trap cleanup EXIT

free_port() { python3 -c "import socket;s=socket.socket();s.bind(('127.0.0.1',0));print(s.getsockname()[1]);s.close()"; }

echo "Starting Redis"
docker run --rm -d --name "$redis" -p 127.0.0.1::6379 redis:7 >/dev/null
redis_port="$(docker port "$redis" 6379/tcp | cut -d: -f2)"
for _ in $(seq 1 30); do
  docker exec "$redis" redis-cli ping >/dev/null 2>&1 && break
  sleep 1
done

# A real signing key and a real token: cart-service authenticates every request that touches
# a cart, so a drill without one could only reach /health, which reads nothing.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/jwt.pem" 2>/dev/null
openssl rsa -in "$work/jwt.pem" -pubout -outform DER -out "$work/jwt.pub.der" 2>/dev/null
public_key="$(base64 < "$work/jwt.pub.der" | tr -d '\n')"
token="$(python3 "$repo_root/ops/testing/mint-access-token.py" "$work/jwt.pem" drill-key shopper-1)"

# Reuse a jar only when it is newer than the source it claims to be built from. Taking any
# jar that happens to exist is how this drill spent its first run measuring a build that
# predated the Redis timeout it was written to verify -- reporting a 60-second stall as a
# property of the service rather than of the artifact. The k8s drill learned the same lesson
# about container images; a target/ directory is no different.
jar="$(ls backend/cart-service/target/cart-service-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
if [ -n "$jar" ]; then
  if newer="$(find backend/cart-service/src/main backend/cart-service/pom.xml \
        backend/platform-security/src/main -type f -newer "$jar" -print -quit 2>/dev/null)" \
     && [ -n "$newer" ]; then
    echo "Rebuilding: $jar is older than $newer"
    jar=""
  fi
fi
if [ -z "$jar" ]; then
  mvn -f backend/pom.xml -pl cart-service -am -o -q package -DskipTests
  jar="$(ls backend/cart-service/target/cart-service-*.jar | grep -v sources | head -1)"
fi

# The property under test is a configuration value, so assert it actually shipped rather
# than inferring it from behaviour that has several possible causes.
unzip -p "$jar" BOOT-INF/classes/application.yml 2>/dev/null | grep -q "timeout:" || {
  echo "FAIL: the packaged cart-service has no Redis timeout configured." >&2
  echo "      Checks 2 and 3 would be measuring Lettuce's 60-second default." >&2
  exit 1; }

app_port="$(free_port)"
echo "Starting cart-service"
SERVER_PORT="$app_port" \
SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT="$redis_port" \
SECURITY_JWT_ACTIVE_KEY_ID=drill-key \
SECURITY_JWT_PUBLIC_KEY_BASE64="$public_key" \
INTERNAL_SERVICE_TOKEN=chaos_cart_internal_token_value \
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0 \
  java -jar "$jar" > "$work/app.log" 2>&1 &
app_pid=$!

for _ in $(seq 1 120); do
  curl -sf "http://127.0.0.1:${app_port}/api/v1/cart/health" >/dev/null 2>&1 && break
  kill -0 "$app_pid" 2>/dev/null || { echo "cart-service exited during startup" >&2; tail -30 "$work/app.log" >&2; exit 1; }
  sleep 1
done
curl -sf "http://127.0.0.1:${app_port}/api/v1/cart/health" >/dev/null

auth=(-H "Authorization: Bearer ${token}")

# Prints "<http status> <elapsed seconds> <item count, or -1 when the body is not a cart>"
read_cart() {
  local code
  code="$(curl -s -o "$work/body.json" -w '%{http_code} %{time_total}' "${auth[@]}" \
    "http://127.0.0.1:${app_port}/api/v1/cart/shopper-1")"
  local items
  items="$(python3 -c "
import json
try:
    print(len(json.load(open('$work/body.json'))['data']['items']))
except Exception:
    print(-1)
")"
  echo "$code $items"
}

checks=0

echo "1. Baseline: an item added to the cart is read back"
# Deliberately not `curl -sf`: with -f curl discards the response body on an HTTP error, so
# the one thing needed to explain the failure is the thing thrown away.
add_status="$(curl -s -o "$work/add.json" -w '%{http_code}' \
  -X POST "http://127.0.0.1:${app_port}/api/v1/cart/shopper-1/items" "${auth[@]}" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: chaos-drill-0001' \
  -d '{"productId":"1","productSlug":"chaos-probe-backpack","name":"Chaos Probe Backpack","price":2199,"currency":"INR","quantity":2}')"
[ "$add_status" = "200" ] || [ "$add_status" = "201" ] || {
  echo "FAIL: adding an item returned ${add_status}" >&2
  echo "      body: $(cat "$work/add.json" 2>/dev/null)" >&2
  grep -iE "error|exception|denied|invalid" "$work/app.log" | tail -10 >&2
  exit 1; }
read -r code seconds items <<< "$(read_cart)"
[ "$code" = "200" ] && [ "$items" -ge 1 ] || {
  echo "FAIL: baseline read returned ${code} with ${items} item(s)" >&2; tail -20 "$work/app.log" >&2; exit 1; }
checks=$((checks + 1))
echo "   200 with ${items} item(s)"

echo "2. Redis paused: a read must fail loudly, never 200 with an empty cart"
docker pause "$redis" >/dev/null
sleep 1
slowest=0
verdict=""
for _ in $(seq 1 5); do
  read -r code seconds items <<< "$(read_cart)"
  observed="$(python3 -c "print(int(float('$seconds') * 1000))")"
  [ "$observed" -gt "$slowest" ] && slowest="$observed"
  if [ "$code" = "200" ] && [ "$items" = "0" ]; then
    echo "FAIL: cart-service answered 200 with an empty cart while Redis was unreachable." >&2
    echo "      A shopper whose cart is intact would be shown an empty one, with no error" >&2
    echo "      anywhere. A connection failure must not collapse into the same path as a" >&2
    echo "      legitimately absent cart." >&2
    docker unpause "$redis" >/dev/null 2>&1 || true
    exit 1
  fi
  if [ "$code" != "200" ]; then verdict="$code"; break; fi
  sleep 1
done
[ -n "$verdict" ] || {
  echo "FAIL: cart-service never reported a failure while Redis was unreachable (last body had ${items} items)" >&2
  docker unpause "$redis" >/dev/null 2>&1 || true
  exit 1; }
checks=$((checks + 1))
echo "   answered ${verdict} rather than a false empty cart"

echo "3. And it failed quickly rather than waiting out the client timeout"
# Redis is the store here, so 2 seconds is deliberately more generous than the 250ms search
# allows its cache; the point is that it is bounded at all. On Lettuce's 60-second default
# every request thread blocks until the pool is exhausted, which turns a degraded dependency
# into a dead service.
budget_ms="${CHAOS_CART_BUDGET_MS:-8000}"
[ "$slowest" -le "$budget_ms" ] || {
  echo "FAIL: the read took ${slowest}ms with Redis stalled (budget ${budget_ms}ms)." >&2
  echo "      Check spring.data.redis.timeout in cart-service; Lettuce defaults to 60s." >&2
  docker unpause "$redis" >/dev/null 2>&1 || true
  exit 1; }
checks=$((checks + 1))
echo "   slowest read was ${slowest}ms"

echo "4. Redis back: the cart is intact and the service recovered without a restart"
docker unpause "$redis" >/dev/null
recovered=""
for _ in $(seq 1 20); do
  read -r code seconds items <<< "$(read_cart)"
  if [ "$code" = "200" ] && [ "$items" -ge 1 ]; then recovered="$items"; break; fi
  sleep 1
done
[ -n "$recovered" ] || {
  echo "FAIL: the cart did not come back after Redis returned" >&2; exit 1; }
checks=$((checks + 1))
echo "   recovered with ${recovered} item(s) still in the cart"

echo
echo "Cart chaos drill passed ${checks} checks: an unreachable store fails loudly instead of"
echo "showing an empty cart, fails within its timeout budget, and recovers with the cart intact."
