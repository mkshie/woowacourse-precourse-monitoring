import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';

export const options = {
  scenarios: {
    normal_traffic: {
      executor: 'constant-arrival-rate',
      exec: 'normalTraffic',
      rate: 5,
      timeUnit: '1s',
      duration: '2m30s',
      preAllocatedVUs: 5,
      maxVUs: 15,
      tags: { phase: 'baseline-and-recovery' },
    },
    injected_errors: {
      executor: 'constant-arrival-rate',
      exec: 'errorTraffic',
      startTime: '30s',
      rate: 1,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 2,
      maxVUs: 5,
      tags: { phase: 'incident' },
    },
    injected_latency: {
      executor: 'constant-arrival-rate',
      exec: 'slowTraffic',
      startTime: '30s',
      rate: 1,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 3,
      maxVUs: 8,
      tags: { phase: 'incident' },
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.20'],
    'http_req_duration{scenario:injected_latency}': [
      'p(95)>1000',
      'p(95)<2500',
    ],
  },
};

function run(mode, expectedStatus) {
  const response = http.post(
    `${BASE_URL}/monitoring/incident?mode=${mode}`,
    null,
    { tags: { operation: 'monitoring-incident', mode: mode.toLowerCase() } },
  );

  check(response, {
    [`${mode} returns ${expectedStatus}`]: (result) =>
      result.status === expectedStatus,
  });
}

export function normalTraffic() {
  run('NORMAL', 204);
}

export function errorTraffic() {
  run('ERROR', 500);
}

export function slowTraffic() {
  run('SLOW', 204);
}
