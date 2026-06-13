import http from 'k6/http';

export const BASE    = __ENV.BASE_URL || 'http://localhost:8080';
export const API_KEY = __ENV.API_KEY  || 'password';

export const apiHeaders = {
  'Content-Type': 'application/json',
  'X-API-Key': API_KEY,
};

export const trendStats = ['avg', 'med', 'p(95)', 'p(99)', 'max'];

export function createWebhook(payload) {
  const res = http.post(
    `${BASE}/api/webhooks`,
    JSON.stringify(payload),
    { headers: apiHeaders },
  );
  if (res.status !== 201 && res.status !== 200) {
    throw new Error(`createWebhook failed: HTTP ${res.status} ${res.body}`);
  }
  const body = res.json();
  return { id: body.id, slug: body.slug };
}

export function makeHandleSummary(scenario) {
  return function handleSummary(data) {
    const vals = (key) => data.metrics[key]?.values ?? {};
    const dur    = vals('http_req_duration');
    const reqs   = vals('http_reqs');
    const failed = vals('http_req_failed');

    const out = {
      scenario,
      cache:      __ENV.CACHE_MODE || 'unknown',
      rps:        round(reqs.rate),
      p50:        round(dur['med']),
      p95:        round(dur['p(95)']),
      p99:        round(dur['p(99)']),
      avg:        round(dur['avg']),
      max:        round(dur['max']),
      error_rate: round((failed.rate || 0) * 100, 3),
      iterations: data.metrics.iterations?.values.count ?? 0,
    };

    const line =
      `[${out.cache}] ${scenario}: ${out.rps} req/s | ` +
      `p50 ${out.p50}ms | p95 ${out.p95}ms | p99 ${out.p99}ms | err ${out.error_rate}%\n`;

    const result = { stdout: line };
    if (__ENV.SUMMARY_OUT) {
      result[__ENV.SUMMARY_OUT] = JSON.stringify(out, null, 2);
    }
    return result;
  };
}

function round(v, digits = 2) {
  if (v === undefined || v === null || Number.isNaN(v)) return 0;
  const f = Math.pow(10, digits);
  return Math.round(v * f) / f;
}

export function workloadPayload(workload, mockPort) {
  const port = mockPort || __ENV.MOCK_PORT || '9099';

  if (workload === 'proxy') {
    return {
      name:            `cap-proxy-${Date.now()}`,
      methods:         'POST',
      debugMode:       false,
      proxyUrl:        `http://host.docker.internal:${port}/`,
      requestTemplate: '{"event":"{{body.event}}","branch":"{{body.branch}}",' +
                       '"urgent":"{{#if body.urgent}}YES{{else}}no{{/if}}"}',
      maxLogCount:     100,
    };
  }

  return {
    name:        `cap-simple-${Date.now()}`,
    methods:     'POST',
    debugMode:   false,
    maxLogCount: 100,
  };
}

export function fireWorkload(workload, data, iter) {
  if (workload === 'crud') {
    const payload = JSON.stringify({
      name:        `cap-crud-${__VU}-${Date.now()}`,
      methods:     'POST',
      debugMode:   false,
      maxLogCount: 20,
    });
    const create = http.post(`${BASE}/api/webhooks`, payload, { headers: apiHeaders });
    if (create.status !== 201) return create;

    const id = create.json('id');
    http.get(`${BASE}/api/webhooks/${id}`, { headers: apiHeaders });
    http.patch(`${BASE}/api/webhooks/${id}/toggle`, null, { headers: apiHeaders });
    return http.del(`${BASE}/api/webhooks/${id}`, null, { headers: apiHeaders });
  }

  const body = workload === 'proxy'
    ? JSON.stringify({ event: 'push', branch: 'main', urgent: iter % 2 === 0 })
    : JSON.stringify({ event: 'push', n: iter });

  return http.post(
    `${BASE}/webhook/${data.slug}`,
    body,
    { headers: { 'Content-Type': 'application/json' } },
  );
}

export function makeCapacitySummary(meta) {
  return function handleSummary(data) {
    const m      = data.metrics;
    const vals   = (key) => m[key]?.values ?? {};
    const dur    = vals('http_req_duration');
    const reqs   = vals('http_reqs');
    const failed = vals('http_req_failed');

    const dropped = m.dropped_iterations?.values.count ?? 0;
    const vusMax  = m.vus_max ? (m.vus_max.values.max ?? m.vus_max.values.value ?? 0) : 0;
    const target  = Number(__ENV.TARGET_RPS || 0);
    const iterRate = m.iterations ? m.iterations.values.rate : (reqs.rate || 0);
    const achieved = round(iterRate);

    const out = {
      phase:            meta.phase || 'capacity',
      workload:         __ENV.WORKLOAD || 'simple',
      cache:            __ENV.CACHE_MODE || 'unknown',
      verticles:        Number(__ENV.VERTICLES || 0),
      target_rps:       target,
      achieved_rps:     achieved,
      http_rps:         round(reqs.rate),
      achieved_ratio:   target > 0 ? round(achieved / target, 4) : 0,
      p50:              round(dur['med']),
      p95:              round(dur['p(95)']),
      p99:              round(dur['p(99)']),
      avg:              round(dur['avg']),
      max:              round(dur['max']),
      error_rate:       round((failed.rate || 0) * 100, 4),
      dropped_iterations: dropped,
      vus_max:          vusMax,
      iterations:       m.iterations?.values.count ?? 0,
    };

    const line =
      `[${out.cache} v${out.verticles} ${out.workload}] ${out.phase} ` +
      `tgt=${target} ach=${achieved} rps | ` +
      `p50 ${out.p50} p95 ${out.p95} p99 ${out.p99} ms | ` +
      `err ${out.error_rate}% drop ${dropped} vus ${vusMax}\n`;

    const result = { stdout: line };
    if (__ENV.SUMMARY_OUT) result[__ENV.SUMMARY_OUT] = JSON.stringify(out, null, 2);
    return result;
  };
}
