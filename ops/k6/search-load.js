import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEARCH_PATH = __ENV.SEARCH_PATH || '/api/v1/search';
const SIZE = Number(__ENV.SEARCH_SIZE || 20);

const queries = (__ENV.SEARCH_QUERIES || 'iphone,laptop,headphones,comfortable running shoes,mobile')
  .split(',')
  .map((query) => query.trim())
  .filter(Boolean);

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: Number(__ENV.WARMUP_VUS || 10),
      duration: __ENV.WARMUP_DURATION || '1m',
      gracefulStop: '10s',
    },
    benchmark: {
      executor: 'constant-vus',
      vus: Number(__ENV.BENCHMARK_VUS || 50),
      duration: __ENV.BENCHMARK_DURATION || '10m',
      startTime: __ENV.WARMUP_DURATION || '1m',
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const query = queries[__ITER % queries.length];
  const response = http.get(`${BASE_URL}${SEARCH_PATH}?q=${encodeURIComponent(query)}&page=0&size=${SIZE}`, {
    tags: { endpoint: 'search', query },
  });

  check(response, {
    'search status is 200': (res) => res.status === 200,
    'search response has body': (res) => Boolean(res.body && res.body.length > 0),
  });

  sleep(Number(__ENV.SLEEP_SECONDS || 0.1));
}

export function handleSummary(data) {
  return {
    'ops/k6/results/search-load-summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data),
  };
}

function textSummary(data) {
  const metrics = data.metrics;
  const duration = metrics.http_req_duration;
  const failed = metrics.http_req_failed;
  const requests = metrics.http_reqs;

  return [
    '',
    'k6 search load summary',
    `requests/sec: ${requests?.rate?.toFixed(2) ?? 'n/a'}`,
    `total requests: ${requests?.count ?? 'n/a'}`,
    `error rate: ${failed?.rate != null ? (failed.rate * 100).toFixed(2) + '%' : 'n/a'}`,
    `avg latency: ${duration?.avg?.toFixed(2) ?? 'n/a'} ms`,
    `p95 latency: ${duration?.['p(95)']?.toFixed(2) ?? 'n/a'} ms`,
    `p99 latency: ${duration?.['p(99)']?.toFixed(2) ?? 'n/a'} ms`,
    '',
  ].join('\n');
}
