/* global Response, Headers */
import assert from 'node:assert/strict';
import test from 'node:test';
import { createAuthenticatedRequest, ApiRequestError, ActiveTokenExpiredError, SessionExpiredError } from '../src/authenticatedRequest.ts';

function storage() {
  const values = new Map([
    ['ticksy.accessToken', 'old-access'], ['ticksy.refreshToken', 'refresh'], ['ticksy.activeToken.1', 'active'],
  ]);
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) };
}
const json = (body, status = 200) => new Response(JSON.stringify(body), { status });
const tokens = { accessToken: 'new-access', refreshToken: 'new-refresh' };

test('동시 401은 한 번 갱신하고 기존 본문과 active-token을 유지해 재전송한다', async () => {
  const saved = storage();
  let refreshCount = 0;
  let retries = 0;
  const request = createAuthenticatedRequest({ storage: saved, fetch: async (url, options) => {
    if (url.includes('/reissue')) {
      refreshCount++;
      assert.equal(new Headers(options.headers).get('Authorization-Refresh'), 'Bearer refresh');
      return json(tokens);
    }
    assert.equal(options.body, '{"idempotencyKey":"same-key"}');
    assert.equal(new Headers(options.headers).get('X-Active-Token'), 'active');
    if (new Headers(options.headers).get('Authorization') === 'Bearer old-access') return json({}, 401);
    retries++;
    return json({ success: true });
  }});
  const options = { method: 'POST', headers: { 'X-Active-Token': 'active' }, body: '{"idempotencyKey":"same-key"}' };
  await Promise.all([request('/checkout', options), request('/checkout', options), request('/checkout', options)]);
  assert.equal(refreshCount, 1);
  assert.equal(retries, 3);
  assert.equal(saved.getItem('ticksy.activeToken.1'), 'active');
});

test('갱신 중 503 또는 네트워크 오류가 발생해도 토큰을 지우지 않고 다시 시도할 수 있다', async () => {
  for (const networkFailure of [false, true]) {
    const saved = storage();
    let unavailable = true;
    const request = createAuthenticatedRequest({ storage: saved, fetch: async (url, options) => {
      if (url.includes('/reissue')) {
        if (unavailable) {
          if (networkFailure) throw new TypeError('Failed to fetch');
          return json({}, 503);
        }
        return json(tokens);
      }
      return new Headers(options.headers).get('Authorization') === 'Bearer old-access' ? json({}, 401) : json({ ok: true });
    }});
    await assert.rejects(request('/checkout', {}), ApiRequestError);
    assert.equal(saved.getItem('ticksy.refreshToken'), 'refresh');
    assert.equal(saved.getItem('ticksy.activeToken.1'), 'active');
    unavailable = false;
    assert.deepEqual(await request('/checkout', {}), { ok: true });
  }
});

test('active-token 만료와 일시적인 서버 장애는 다른 오류로 전달한다', async () => {
  const saved = storage();
  const request = createAuthenticatedRequest({ storage: saved, fetch: async (url) => {
    if (url === '/expired') return json({ code: 'ACTIVE_TOKEN_EXPIRED' }, 410);
    if (url === '/busy') return json({}, 429);
    return json({ code: 'QUEUE_UNAVAILABLE' }, 503);
  }});
  await assert.rejects(request('/expired', {}), ActiveTokenExpiredError);
  await assert.rejects(request('/busy', {}), ApiRequestError);
  await assert.rejects(request('/unavailable', {}), ApiRequestError);
  assert.equal(saved.getItem('ticksy.activeToken.1'), 'active');
});

test('실제 refresh 인증 실패는 로그인 만료로 처리하며 active-token은 유지한다', async () => {
  const saved = storage();
  let count = 0;
  const request = createAuthenticatedRequest({ storage: saved, fetch: async () => { count++; return json({}, 401); } });
  await assert.rejects(request('/checkout', {}), SessionExpiredError);
  assert.equal(count, 2);
  assert.equal(saved.getItem('ticksy.accessToken'), null);
  assert.equal(saved.getItem('ticksy.activeToken.1'), 'active');
});

test('일반 서버 오류 이후 같은 팝업에서 재시도할 수 있다', async () => {
  const saved = storage();
  let count = 0;
  const request = createAuthenticatedRequest({ storage: saved, fetch: async () => ++count === 1 ? json({}, 500) : json({ ok: true }) });
  await assert.rejects(request('/checkout', {}), ApiRequestError);
  assert.deepEqual(await request('/checkout', {}), { ok: true });
  assert.equal(saved.getItem('ticksy.accessToken'), 'old-access');
});

test('일반 403과 로그인 실패에는 refresh를 실행하지 않는다', async () => {
  let count = 0;
  const request = createAuthenticatedRequest({ storage: storage(), fetch: async (url) => { count++; return json({}, url.includes('/login') ? 401 : 403); } });
  await assert.rejects(request('/checkout', {}), ApiRequestError);
  await assert.rejects(request('/auth/login', {}), ApiRequestError);
  assert.equal(count, 2);
});
