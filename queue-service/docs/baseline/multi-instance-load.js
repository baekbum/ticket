import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Counter } from 'k6/metrics';

const registered = new Counter('shared_registered');
const rejected = new Counter('shared_limited');
const ready = new Counter('distinct_ready');
const waiting = new Counter('distinct_waiting');
const ports = [18084, 18085];
export const options = {
  scenarios: {
    shared: { executor: 'per-vu-iterations', vus: 24, iterations: 1, exec: 'shared', maxDuration: '30s' },
    distinct: { executor: 'per-vu-iterations', vus: 12, iterations: 1, exec: 'distinct', maxDuration: '30s' },
  },
  thresholds: { checks: ['rate==1'], http_req_failed: ['rate==0'] },
};
function bearer(user) {
  const now = Math.floor(Date.now() / 1000);
  const head = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
  const body = encoding.b64encode(JSON.stringify({
    sub: user, auth: 'ROLE_USER', tokenType: 'access', tokenVersion: 1, iat: now, exp: now + 7200,
  }), 'rawurl');
  const data = head + '.' + body;
  return 'Bearer ' + data + '.' + crypto.hmac('sha256', 'queue-baseline-only-secret-at-least-32-bytes-long', data, 'base64rawurl');
}
function enter(user, event) {
  const index = __VU % 2;
  const headers = { Authorization: bearer(user) };
  const base = 'http://host.docker.internal:' + ports[index] + '/api/v1/queue/events/' + event;
  const response = http.post(base + '/enter', null, { headers });
  check(response, { 'registration HTTP 200': r => r.status === 200 });
  return { response, headers, other: 'http://host.docker.internal:' + ports[1 - index] + '/api/v1/queue/events/' + event };
}
export function shared() {
  const entered = enter('cluster-shared', 930001);
  const data = entered.response.json();
  check(data, { 'shared session limit contract': d => ['WAITING', 'SESSION_LIMIT_CONFIRM_REQUIRED'].includes(d.status) });
  if (data.status === 'WAITING') registered.add(1);
  else rejected.add(1);
}
export function distinct() {
  const entered = enter('cluster-user-' + __VU, 930002);
  const data = entered.response.json();
  check(data, { 'distinct WAITING': d => d.status === 'WAITING' && !!d.token });
  const response = http.get(entered.other + '/status', {
    headers: Object.assign({}, entered.headers, { 'X-Waiting-Token': data.token }),
  });
  const status = response.json();
  check(response, { 'other instance sees session': r => r.status === 200 && ['WAITING', 'READY'].includes(status.status) });
  if (status.status === 'READY') ready.add(1);
  else waiting.add(1);
}
export function handleSummary(data) {
  return { '/results/summary.json': JSON.stringify(data, null, 2) };
}