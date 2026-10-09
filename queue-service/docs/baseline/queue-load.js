import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Counter, Trend } from 'k6/metrics';

const base = __ENV.BASE_URL || 'http://host.docker.internal:18083';
const runId = __ENV.RUN_ID || 'mvc-baseline';
const eventId = Number(__ENV.EVENT_ID || 900001);
const vus = Number(__ENV.VUS || 100);
const duration = __ENV.DURATION || '60s';
const pollSeconds = Number(__ENV.POLL_SECONDS || 1);
const holdSeconds = Number(__ENV.HOLD_SECONDS || 5);
const mode = __ENV.MODE || 'lifecycle';
const secret = 'queue-baseline-only-secret-at-least-32-bytes-long';
const admitted = new Counter('queue_admitted');
const completed = new Counter('queue_completed');
const polling = new Trend('queue_status_duration', true);
export const options = {
  scenarios: { queue: { executor: 'constant-vus', vus, duration, gracefulStop: '15s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] },
};
let token;
let authorization;
function auth() {
  if (!authorization) {
    const now = Math.floor(Date.now() / 1000);
    const head = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }), 'rawurl');
    const body = encoding.b64encode(JSON.stringify({
      sub: runId + '-' + __VU, auth: 'ROLE_USER', tokenType: 'access',
      tokenVersion: 1, iat: now, exp: now + 7200,
    }), 'rawurl');
    const data = head + '.' + body;
    authorization = 'Bearer ' + data + '.' + crypto.hmac('sha256', secret, data, 'base64rawurl');
  }
  return authorization;
}
function request(method, action, body, extra = {}) {
  return http.request(method, base + '/api/v1/queue' + action, body == null ? null : JSON.stringify(body), {
    headers: Object.assign({ Authorization: auth(), 'Content-Type': 'application/json' }, extra),
    tags: { name: action.replace(String(eventId), ':eventId') }, timeout: '10s',
  });
}
function parsed(response) {
  try { return response.json(); } catch (_) { return {}; }
}
export default function () {
  if (!token) {
    const entered = request('POST', '/events/' + eventId + '/enter', null);
    const data = parsed(entered);
    if (!check(entered, { 'enter WAITING': r => r.status === 200 && data.status === 'WAITING' && !!data.token })) {
      sleep(pollSeconds); return;
    }
    token = data.token;
  }
  const response = request('GET', '/events/' + eventId + '/status', null, { 'X-Waiting-Token': token });
  polling.add(response.timings.duration);
  const data = parsed(response);
  if (!check(response, { 'status valid': r => r.status === 200 && ['WAITING', 'READY'].includes(data.status) && !!data.token })) {
    // Do not hide failed transitions by force-entering or retrying mutations.
    sleep(pollSeconds); return;
  }
  if (data.status === 'READY') {
    admitted.add(1);
    // In waiting mode admitted users hold slots for the rest of the run.
    if (mode === 'waiting') { sleep(3600); return; }
    sleep(holdSeconds);
    const done = request('POST', '/complete', {
      eventId, userId: runId + '-' + __VU, token: data.token,
    });
    if (check(done, { 'complete 204': r => r.status === 204 })) completed.add(1);
    token = null;
  }
  sleep(pollSeconds);
}
export function handleSummary(data) {
  return { '/results/summary.json': JSON.stringify({
    parameters: { base, runId, eventId, vus, duration, pollSeconds, holdSeconds, mode },
    summary: data,
  }, null, 2) };
}
