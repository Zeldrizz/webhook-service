// CI smoke / SLO gate — self-contained, no lib/ imports.
// Runs 200 RPS for 30s. Fails build if p99 > 200ms or error rate > 1%.
import { check } from 'k6';
import http from 'k6/http';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'password';
const TARGET_RPS = Number(__ENV.TARGET_RPS || 200);

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const res = http.post(
    `${BASE}/api/webhooks`,
    JSON.stringify({ name: 'ci-smoke', methods: 'POST,GET', debugMode: false }),
    { headers: { 'Content-Type': 'application/json', 'X-API-Key': API_KEY } },
  );
  if (res.status !== 201 && res.status !== 200) {
    throw new Error(`createWebhook failed: HTTP ${res.status} ${res.body}`);
  }
  return { slug: res.json('slug') };
}

export default function (data) {
  const res = http.post(
    `${BASE}/webhook/${data.slug}`,
    '{"event":"ci.check","source":"k6"}',
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
}
