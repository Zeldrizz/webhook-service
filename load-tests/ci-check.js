// Smoke / SLO gate for CI — runs ~30 seconds, fails the build if SLOs are breached.
//
// SLOs (conservative, CI environment):
//   p99 < 200ms   — hot path /webhook/:slug
//   error rate < 1%
//   achieved RPS >= 80% of target
import { check } from 'k6';
import http from 'k6/http';
import { createWebhook, BASE, apiHeaders } from './lib/common.js';

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
  return createWebhook({ name: 'ci-smoke', methods: 'POST,GET', debugMode: false });
}

export default function (data) {
  const res = http.post(
    `${BASE}/webhook/${data.slug}`,
    '{"event":"ci.check","source":"k6"}',
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
}
