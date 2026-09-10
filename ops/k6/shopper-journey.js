import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// The traffic shape lives in traffic-model.json, not here, so the assumptions can be
// argued with in one place instead of being buried in control flow. Every number in it is
// assumed rather than observed, and the file says so.
//
// It replaces an inline distribution that converted 20% of sessions to checkout. Real
// e-commerce conversion is roughly 1-3%, so that overstated write load by about ten times
// and would have made checkout look like the dominant cost of a shopper session.
const MODEL = JSON.parse(open(__ENV.TRAFFIC_MODEL || './traffic-model.json'));
const MIX = MODEL.session_mix;
const PACING = MODEL.pacing.think_time_seconds;
const SHOPPERS = MODEL.shoppers;

// Cumulative thresholds, deepest journey last.
const P_CHECKOUT = MIX.completes_checkout;
const P_CART = P_CHECKOUT + MIX.adds_to_cart;
const P_SEARCH = P_CART + MIX.browse_and_search;

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// One token per synthetic shopper, because each acts as itself. A single token whose
// subject is "load-shopper" cannot POST to /cart/load-shopper-7/items: CartController
// rejects accessing another user's cart with 403, so every cart write failed and, because
// the journey stops at a failed step, no checkout ever ran. The benchmark reported a clean
// 0% checkout failure rate while never completing a checkout.
const ACCESS_TOKENS = __ENV.ACCESS_TOKENS
  ? JSON.parse(__ENV.ACCESS_TOKENS)
  : (__ENV.ACCESS_TOKEN ? [{ subject: __ENV.USER_ID || 'load-shopper', token: __ENV.ACCESS_TOKEN }] : []);
const ACCESS_TOKEN = ACCESS_TOKENS.length ? ACCESS_TOKENS[0].token : '';
// One cart per synthetic shopper by default. A single shared cart forces maximal write
// contention on one key, which is a useful pathological test but is not what real traffic
// looks like; traffic-model.json selects between them and they must not be compared.
// A shopper and the token that authenticates as exactly that shopper, chosen together.
function shopper() {
  if (SHOPPERS.mode === 'shared' || ACCESS_TOKENS.length === 1) return ACCESS_TOKENS[0];
  return ACCESS_TOKENS[Math.floor(Math.random() * ACCESS_TOKENS.length)];
}

// Deterministic, well-distributed [0,1). Two rounds of xorshift-multiply, which is enough
// avalanche that adjacent (VU, iteration) pairs do not land in adjacent buckets.
function hashedBucket(vu, iter) {
  let x = (Math.imul(vu, 2654435761) + Math.imul(iter, 40503)) >>> 0;
  x ^= x >>> 15;
  x = Math.imul(x, 2246822507) >>> 0;
  x ^= x >>> 13;
  return (x >>> 0) / 4294967296;
}

// Think time. Without it a journey completes as fast as the network allows, so the test
// holds far fewer sessions open than real traffic does at the same request rate.
function think() {
  sleep(PACING.min + Math.random() * (PACING.max - PACING.min));
}
const PAYMENT_TOKEN = __ENV.PAYMENT_TOKEN || 'pm_loadtest_token';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

if (!ACCESS_TOKEN) {
  throw new Error('ACCESS_TOKEN is required; mint an RS256 token with ops/testing/mint-access-token.py');
}

const browseAttempts = new Counter('journey_browse_attempts');
const searchAttempts = new Counter('journey_search_attempts');
const cartAttempts = new Counter('journey_cart_attempts');
const checkoutAttempts = new Counter('journey_checkout_attempts');
const stepFailures = new Rate('journey_step_failed');

export const options = {
  scenarios: {
    shopper_journey: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.TARGET_RPS || 5),
      timeUnit: '1s',
      duration: __ENV.BENCHMARK_DURATION || '2m',
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 200),
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    journey_step_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function headersFor(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}
const authHeaders = headersFor(ACCESS_TOKEN);

function checked(response, name, expectedStatus) {
  const ok = check(response, {
    [`${name} status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
  });
  stepFailures.add(!ok, { step: name });
  return ok;
}

function parse(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

export default function () {
  // One shopper per iteration, and the same shopper -- and token -- for every step of it.
  const actor = shopper();
  const userId = actor.subject;
  const actorHeaders = headersFor(actor.token);
  // Deterministic buckets make the invented split reproducible instead of merely
  // probable. Every iteration browses; half stop there, thirty percent search, and twenty
  // percent traverse all the way through the write path.
  // A hashed bucket, not (VU * prime + ITER) % 1000.
  //
  // That formula looked deterministic and uniform and was neither at these rates. With about
  // fifty virtual users it only ever visits ~50 of the 1000 residues, none of which fell
  // below the 1.2% conversion threshold -- so 930 iterations produced ZERO checkouts and the
  // benchmark silently measured no write path at all while reporting success. A sampler that
  // never reaches the branch you care about is worse than a random one.
  //
  // This keeps reproducibility (same VU and iteration give the same bucket) while actually
  // spreading across the range.
  const bucket = hashedBucket(__VU, __ITER);
  const page = (__VU * 101 + __ITER) % 50;

  browseAttempts.add(1);
  const browse = http.get(`${BASE_URL}/api/v1/products?page=${page}&size=12`, {
    tags: { step: 'browse' },
  });
  if (!checked(browse, 'browse', 200)) return;
  const browseBody = parse(browse);
  const products = browseBody && browseBody.data;
  const product = Array.isArray(products) && products.length
    ? products[(__VU + __ITER) % products.length]
    : null;
  const productOk = check(product, { 'browse returned a product': (p) => Boolean(p && p.id && p.slug) });
  stepFailures.add(!productOk, { step: 'browse_payload' });
  think();
  if (!productOk || bucket >= P_SEARCH) return;

  searchAttempts.add(1);
  const search = http.get(
    `${BASE_URL}/api/v1/search?q=${encodeURIComponent(product.name)}&page=0&size=10`,
    { tags: { step: 'search' } },
  );
  if (!checked(search, 'search', 200)) return;
  const searchBody = parse(search);
  const matches = searchBody && searchBody.items;
  const selected = Array.isArray(matches) && matches.length ? matches[0] : product;
  think();
  if (bucket >= P_CART) return;

  const idempotencySuffix = `${RUN_ID}-${__VU}-${__ITER}`;
  cartAttempts.add(1);
  const cart = http.post(
    `${BASE_URL}/api/v1/cart/${encodeURIComponent(userId)}/items`,
    JSON.stringify({
      productId: String(selected.id),
      productSlug: selected.slug,
      name: selected.name,
      price: selected.price,
      currency: selected.currency,
      quantity: 1,
      imageUrl: selected.imageUrls && selected.imageUrls.length ? selected.imageUrls[0] : null,
    }),
    {
      headers: { ...actorHeaders, 'Idempotency-Key': `cart-${idempotencySuffix}` },
      tags: { step: 'cart' },
    },
  );
  if (!checked(cart, 'cart', 200)) return;

  checkoutAttempts.add(1);
  const checkout = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      userId,
      items: [{ productId: String(selected.id), quantity: 1 }],
      payment: { paymentToken: PAYMENT_TOKEN },
    }),
    {
      headers: { ...actorHeaders, 'Idempotency-Key': `order-${idempotencySuffix}` },
      tags: { step: 'checkout' },
    },
  );
  // 202 means checkout did not complete; this workload counts only persisted PAID orders
  // (201) as successful checkout, matching the order chaos drill's strict assertion.
  checked(checkout, 'checkout', 201);
}

export function handleSummary(data) {
  const duration = data.metrics.http_req_duration ? data.metrics.http_req_duration.values : {};
  const metricCount = (name) => data.metrics[name] ? data.metrics[name].values.count : 0;
  const summary = {
    schema_version: 1,
    recorded_at_utc: new Date().toISOString(),
    evidence_class: 'invented_synthetic_shopper_journey',
    run: {
      base_url: BASE_URL,
      target_journeys_per_second: Number(__ENV.TARGET_RPS || 5),
      duration: __ENV.BENCHMARK_DURATION || '2m',
      synthetic_shoppers: SHOPPERS,
    },
    traffic_model: MODEL,
    results: {
      http_requests: metricCount('http_reqs'),
      failed_rate: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : null,
      step_failed_rate: data.metrics.journey_step_failed
        ? data.metrics.journey_step_failed.values.rate : null,
      browse_attempts: metricCount('journey_browse_attempts'),
      search_attempts: metricCount('journey_search_attempts'),
      cart_attempts: metricCount('journey_cart_attempts'),
      checkout_attempts: metricCount('journey_checkout_attempts'),
      latency_ms: {
        med: duration.med,
        p95: duration['p(95)'],
        p99: duration['p(99)'],
      },
    },
    limits: [
      'The 50/30/20 journey distribution is invented, not observed shopper traffic.',
      'One synthetic authenticated identity intentionally creates cart write contention.',
      'Payment uses the in-process mock provider; no money moves.',
      'This workload measures local saturation behavior, not production capacity.',
    ],
  };

  const out = __ENV.SUMMARY_OUT || 'ops/k6/results/shopper-journey.json';
  return {
    [out]: JSON.stringify(summary, null, 2),
    stdout: `\nshopper journey: ${summary.results.http_requests} HTTP requests, `
      + `${summary.results.checkout_attempts} checkout attempts, `
      + `${((summary.results.step_failed_rate || 0) * 100).toFixed(3)}% failed steps\n`,
  };
}
