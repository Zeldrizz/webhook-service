import { check } from 'k6';
import {
  createWebhook,
  workloadPayload,
  fireWorkload,
  trendStats,
  makeCapacitySummary,
} from './lib/common.js';

const WORKLOAD     = __ENV.WORKLOAD     || 'simple';
const TARGET_RPS   = Number(__ENV.TARGET_RPS   || 1000);
const DURATION     = __ENV.DURATION     || '25s';
const PREALLOC_VUS = Number(__ENV.PREALLOC_VUS || 200);
const MAX_VUS      = Number(__ENV.MAX_VUS      || 3000);
const PHASE        = __ENV.PHASE        || 'capacity';

export const options = {
  scenarios: {
    [PHASE]: {
      executor:        'constant-arrival-rate',
      rate:            TARGET_RPS,
      timeUnit:        '1s',
      duration:        DURATION,
      preAllocatedVUs: PREALLOC_VUS,
      maxVUs:          MAX_VUS,
    },
  },
  summaryTrendStats: trendStats,
};

export function setup() {
  if (WORKLOAD === 'crud') return {};
  return createWebhook(workloadPayload(WORKLOAD));
}

export default function (data) {
  const res = fireWorkload(WORKLOAD, data, __ITER);
  check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
}

export const handleSummary = makeCapacitySummary({ phase: PHASE });
