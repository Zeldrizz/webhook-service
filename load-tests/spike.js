import { check } from 'k6';
import {
  createWebhook,
  workloadPayload,
  fireWorkload,
  trendStats,
  makeCapacitySummary,
} from './lib/common.js';

const WORKLOAD     = __ENV.WORKLOAD          || 'simple';
const BASE_RPS     = Number(__ENV.SPIKE_BASE_RPS || 1000);
const PEAK_RPS     = Number(__ENV.SPIKE_PEAK_RPS || 12000);
const PREALLOC_VUS = Number(__ENV.PREALLOC_VUS   || 300);
const MAX_VUS      = Number(__ENV.MAX_VUS         || 4000);

export const options = {
  scenarios: {
    spike: {
      executor:        'ramping-arrival-rate',
      startRate:       BASE_RPS,
      timeUnit:        '1s',
      preAllocatedVUs: PREALLOC_VUS,
      maxVUs:          MAX_VUS,
      stages: [
        { target: BASE_RPS, duration: '10s' },
        { target: PEAK_RPS, duration: '5s'  },
        { target: PEAK_RPS, duration: '20s' },
        { target: BASE_RPS, duration: '5s'  },
        { target: BASE_RPS, duration: '15s' },
      ],
    },
  },
  summaryTrendStats: trendStats,
};

export function setup() {
  return createWebhook(workloadPayload(WORKLOAD));
}

export default function (data) {
  const res = fireWorkload(WORKLOAD, data, __ITER);
  check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
}

export const handleSummary = makeCapacitySummary({ phase: 'spike' });
