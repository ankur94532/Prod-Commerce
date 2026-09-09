import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

// A load test is only meaningful if you can say what it measured. This one declares its
// cache regime, draws from a large seeded query set rather than five fixed strings, and
// writes a manifest next to the results so two runs can be compared.
//
// Regimes:
//   cold   every request uses a query no virtual user has issued in this run, so the
//          search path is exercised rather than the cache. This is the pessimistic bound.
//   warm   a small repeating set, which is what the previous benchmark did by accident.
//          Useful as the optimistic bound, and honest only when labelled as such.
//   mixed  a skewed distribution: a head of popular queries plus a long tail. Closest to
//          real traffic shape, still synthetic.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEARCH_PATH = __ENV.SEARCH_PATH || '/api/v1/search';
const SIZE = Number(__ENV.SEARCH_SIZE || 20);
const MODE = __ENV.SEARCH_MODE || '';
const REGIME = (__ENV.CACHE_REGIME || 'cold').toLowerCase();
const QUERY_SET = __ENV.QUERY_SET || 'ops/k6/query-set.json';
const HEAD_SIZE = Number(__ENV.MIXED_HEAD_SIZE || 20);
const HEAD_SHARE = Number(__ENV.MIXED_HEAD_SHARE || 0.7);

const querySet = new SharedArray('queries', () => {
  const parsed = JSON.parse(open(`../../${QUERY_SET}`));
  if (!parsed.queries || parsed.queries.length === 0) {
    throw new Error(`Query set ${QUERY_SET} contains no queries`);
  }
  return parsed.queries;
});

const queryMeta = JSON.parse(open(`../../${QUERY_SET}`));

const distinctQueries = new Counter('distinct_query_requests');
const repeatQueries = new Counter('repeat_query_requests');
const emptyResults = new Counter('zero_result_responses');
const resultCount = new Trend('result_count');

export const options = {
  scenarios: {
    benchmark: {
      executor: 'constant-arrival-rate',
      // Arrival rate, not fixed VUs: a fixed-VU test slows its own request rate when the
      // system slows down, which hides the very degradation it is supposed to reveal.
      rate: Number(__ENV.TARGET_RPS || 200),
      timeUnit: '1s',
      duration: __ENV.BENCHMARK_DURATION || '2m',
      preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 500),
      gracefulStop: '30s',
    },
  },
  thresholds: {
    // Matches the objectives in docs/SLO.md rather than an arbitrary round number.
    http_req_failed: ['rate<0.001'],
    'http_req_duration{expected_response:true}': ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function pickQuery() {
  const total = querySet.length;
  if (REGIME === 'warm') {
    // Deliberately tiny so nearly everything is served from cache.
    return { query: querySet[__ITER % Math.min(5, total)], distinct: false };
  }
  if (REGIME === 'mixed') {
    const head = Math.min(HEAD_SIZE, total);
    if (Math.random() < HEAD_SHARE) {
      return { query: querySet[Math.floor(Math.random() * head)], distinct: false };
    }
    const tailIndex = head + Math.floor(Math.random() * Math.max(1, total - head));
    return { query: querySet[Math.min(tailIndex, total - 1)], distinct: true };
  }
  // cold: walk the set so a given query is issued at most once per pass.
  const index = (__VU * 100003 + __ITER) % total;
  return { query: querySet[index], distinct: true };
}

export default function () {
  const picked = pickQuery();
  const params = [
    `q=${encodeURIComponent(picked.query)}`,
    'page=0',
    `size=${SIZE}`,
  ];
  if (MODE) {
    params.push(`mode=${encodeURIComponent(MODE)}`);
  }

  const response = http.get(`${BASE_URL}${SEARCH_PATH}?${params.join('&')}`, {
    tags: { endpoint: 'search', regime: REGIME, mode: MODE || 'default' },
  });

  if (picked.distinct) {
    distinctQueries.add(1);
  } else {
    repeatQueries.add(1);
  }

  const ok = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  if (ok) {
    try {
      const body = response.json();
      const items = (body && body.items) || [];
      resultCount.add(items.length);
      if (items.length === 0) {
        // A fast, healthy-looking run that returns nothing is the failure mode an empty
        // index produces. Counting it stops that passing for success.
        emptyResults.add(1);
      }
    } catch (error) {
      emptyResults.add(0);
    }
  }
}

export function handleSummary(data) {
  const requests = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
  const failedRate = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : null;
  const duration = data.metrics.http_req_duration ? data.metrics.http_req_duration.values : {};
  const zero = data.metrics.zero_result_responses ? data.metrics.zero_result_responses.values.count : 0;

  const summary = {
    schema_version: 1,
    recorded_at_utc: new Date().toISOString(),
    // Everything needed to reproduce or compare this run.
    run: {
      base_url: BASE_URL,
      search_path: SEARCH_PATH,
      retrieval_mode: MODE || 'server default',
      cache_regime: REGIME,
      target_rps: Number(__ENV.TARGET_RPS || 200),
      duration: __ENV.BENCHMARK_DURATION || '2m',
      page_size: SIZE,
    },
    query_set: {
      path: QUERY_SET,
      count: querySet.length,
      seed: queryMeta.seed,
      corpus_sha256: queryMeta.corpus_sha256,
      provenance: queryMeta.provenance,
    },
    results: {
      requests: requests,
      // k6 counts a request that never completed as failed, so this covers transport
      // errors as well as error statuses.
      failed_rate: failedRate,
      distinct_query_requests: data.metrics.distinct_query_requests
        ? data.metrics.distinct_query_requests.values.count : 0,
      repeat_query_requests: data.metrics.repeat_query_requests
        ? data.metrics.repeat_query_requests.values.count : 0,
      zero_result_responses: zero,
      latency_ms: {
        avg: duration.avg, med: duration.med, p90: duration['p(90)'],
        p95: duration['p(95)'], p99: duration['p(99)'], max: duration.max,
      },
      achieved_rps: data.metrics.http_reqs ? data.metrics.http_reqs.values.rate : null,
    },
    limits: [
      'Queries are catalog-derived synthetic strings, not shopper traffic.',
      'Numbers describe this environment and this data volume only.',
      'A warm-regime result is an upper bound on throughput and must be labelled as one.',
    ],
  };

  const out = __ENV.SUMMARY_OUT || 'ops/k6/results/summary.json';
  const output = {};
  output[out] = JSON.stringify(summary, null, 2);
  output.stdout = `\n${REGIME} regime: ${requests} requests, ` +
    `p95 ${duration['p(95)'] ? duration['p(95)'].toFixed(1) : '?'}ms, ` +
    `failed ${failedRate !== null ? (failedRate * 100).toFixed(3) : '?'}%, ` +
    `zero-result ${zero}\n`;
  return output;
}
