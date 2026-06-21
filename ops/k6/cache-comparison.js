import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const QUERY = __ENV.SEARCH_QUERY || 'iphone';
const SIZE = Number(__ENV.SEARCH_SIZE || 20);
const url = `${BASE_URL}/api/v1/search?q=${encodeURIComponent(QUERY)}&page=0&size=${SIZE}`;

export const options = {
  scenarios: {
    repeated_query: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '2m',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const response = http.get(url, {
    tags: { endpoint: 'search_cache', query: QUERY },
  });

  check(response, {
    'cache workload status is 200': (res) => res.status === 200,
    'cache workload response has body': (res) => Boolean(res.body && res.body.length > 0),
  });
}

export function handleSummary(data) {
  return {
    'ops/k6/results/cache-comparison-summary.json': JSON.stringify(data, null, 2),
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
    'k6 repeated-query cache summary',
    `query: ${QUERY}`,
    `requests/sec: ${requests?.rate?.toFixed(2) ?? 'n/a'}`,
    `total requests: ${requests?.count ?? 'n/a'}`,
    `error rate: ${failed?.rate != null ? (failed.rate * 100).toFixed(2) + '%' : 'n/a'}`,
    `avg latency: ${duration?.avg?.toFixed(2) ?? 'n/a'} ms`,
    `p95 latency: ${duration?.['p(95)']?.toFixed(2) ?? 'n/a'} ms`,
    `p99 latency: ${duration?.['p(99)']?.toFixed(2) ?? 'n/a'} ms`,
    '',
  ].join('\n');
}
