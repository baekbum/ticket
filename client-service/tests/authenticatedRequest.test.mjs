/* global Response, Headers */
import assert from 'node:assert/strict';
import test from 'node:test';
import { createAuthenticatedRequest, ApiRequestError, ActiveTokenExpiredError, SessionExpiredError } from '../src/authenticatedRequest.ts';

function storage() {
  const values = new Map([
    ['ticksy.accessToken', 'old-access'], ['ticksy.refreshToken', 'refresh'],
    ['ticksy.userName', '아이유'], ['ticksy.activeToken.1', 'active'],
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
  await assert.rejects(request('/auth/api/v1/login', {}), ApiRequestError);
  assert.equal(count, 2);
});

test('인그레스의 공개 서비스 경로에는 저장된 토큰을 보내지 않는다', async () => {
  const called = [];
  const request = createAuthenticatedRequest({ storage: storage(), fetch: async (url, options) => {
    called.push(url);
    assert.equal(new Headers(options.headers).get('Authorization'), null);
    return json({ ok: true });
  } });
  await request('/auth/api/v1/login', {});
  await request('/user/api/v1/signup', {});
  await request('/ticket/api/v1/event/cards/festival', {});
  assert.equal(called.length, 3);
});

test('동시 인증 만료는 사용자명도 지우고 로그인 안내를 한 번만 알린다', async () => {
  const saved = storage();
  const messages = [];
  const request = createAuthenticatedRequest({
    storage: saved, fetch: async () => json({}, 401),
    onSessionExpired: (message) => messages.push(message),
  });
  const results = await Promise.allSettled([
    request('/ticket/api/v1/reservation/select', {}), request('/user/api/v1/select/me', {}),
  ]);
  assert.ok(results.every((result) => result.status === 'rejected' && result.reason instanceof SessionExpiredError));
  assert.deepEqual(messages, ['세션이 만료되었습니다. 다시 로그인해주세요.']);
  assert.equal(saved.getItem('ticksy.userName'), null);
  assert.equal(saved.getItem('ticksy.refreshToken'), null);
  assert.equal(saved.getItem('ticksy.activeToken.1'), 'active');
});

test('토큰이 사라진 보호 API는 익명 요청을 보내지 않고 로그인 만료를 알린다', async () => {
  const saved = storage();
  saved.removeItem('ticksy.accessToken');
  saved.removeItem('ticksy.refreshToken');
  let notices = 0;
  const request = createAuthenticatedRequest({
    storage: saved, fetch: async () => { throw new Error('Must not send anonymous protected request'); },
    onSessionExpired: () => { notices++; },
  });
  await assert.rejects(request('/ticket/api/v1/reservation/select', {}), SessionExpiredError);
  await assert.rejects(request('/user/api/v1/select/me', {}), SessionExpiredError);
  assert.equal(notices, 1);
});

test('인증 실패로 명시된 403만 세션 만료로 처리하고 일반 권한 부족은 유지한다', async () => {
  for (const body of [{ code: 'TOKEN_EXPIRED' }, { code: 'INVALID_TOKEN' }, { error: 'Invalid or Tampered Token' }]) {
    const saved = storage();
    let notices = 0;
    const request = createAuthenticatedRequest({
      storage: saved, fetch: async () => json(body, 403), onSessionExpired: () => { notices++; },
    });
    await assert.rejects(request('/ticket/api/v1/reservation/select', {}), SessionExpiredError);
    assert.equal(notices, 1);
    assert.equal(saved.getItem('ticksy.userName'), null);
  }
  const saved = storage();
  const request = createAuthenticatedRequest({
    storage: saved, fetch: async () => json({ code: 'FORBIDDEN' }, 403),
    onSessionExpired: () => { throw new Error('Permission failure must not log out'); },
  });
  await assert.rejects(request('/admin', {}), ApiRequestError);
  assert.equal(saved.getItem('ticksy.userName'), '아이유');
});

test('Access Token만 없으면 남아 있는 Refresh Token으로 복구한다', async () => {
  const saved = storage();
  saved.removeItem('ticksy.accessToken');
  const request = createAuthenticatedRequest({ storage: saved, fetch: async (url, options) => {
    if (url === '/auth/api/v1/reissue') return json(tokens);
    assert.equal(new Headers(options.headers).get('Authorization'), 'Bearer new-access');
    return json({ ok: true });
  }});
  assert.deepEqual(await request('/ticket/api/v1/reservation/select', {}), { ok: true });
  assert.equal(saved.getItem('ticksy.userName'), '아이유');
});

test('이전 토큰의 지연된 인증 403은 새 로그인 상태를 지우지 않는다', async () => {
  const saved = storage();
  let complete;
  const request = createAuthenticatedRequest({
    storage: saved,
    fetch: () => new Promise((resolve) => { complete = resolve; }),
    onSessionExpired: () => { throw new Error('New login must be preserved'); },
  });
  const pending = request('/ticket/api/v1/reservation/select', {});
  saved.setItem('ticksy.accessToken', 'another-login');
  saved.setItem('ticksy.refreshToken', 'another-refresh');
  complete(json({ code: 'INVALID_TOKEN' }, 403));
  await assert.rejects(pending, ApiRequestError);
  assert.equal(saved.getItem('ticksy.accessToken'), 'another-login');
  assert.equal(saved.getItem('ticksy.userName'), '아이유');
});
