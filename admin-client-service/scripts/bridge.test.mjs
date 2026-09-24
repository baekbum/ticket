import assert from 'node:assert/strict';
import { access, readFile, readdir } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';

const code = await readFile(new URL('./bridge.js', import.meta.url), 'utf8');

test('기존 관리자 API를 서비스별 인그레스 경로로 보낸다', async () => {
  const calls = [];
  const messages = [];
  const values = new Map([['admin.accessToken', 'admin-token']]);
  const localStorage = {
    getItem: key => values.get(key) || null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  };
  const window = {
    location: { origin: 'http://localhost' },
    parent: { postMessage: (message, origin) => messages.push({ message, origin }) },
    addEventListener() {},
  };
  const fetch = async (url, options) => {
    calls.push({ url, options });
    return { status: 200, ok: true };
  };
  vm.runInNewContext(code, { window, localStorage, fetch, URL, Headers, FormData, Blob });

  const cases = [
    ['/admin/api/v1/auth/logout', '/auth/api/v1/logout'],
    ['/api/v1/user/select', '/user/api/v1/manage/select'],
    ['/api/v1/user/address/select', '/user/api/v1/manage/address/select'],
    ['/admin/api/v1/event/select', '/ticket/api/v1/manage/event/select'],
    ['/admin/api/v1/seat/cache/inspect', '/ticket/api/v1/manage/seat/cache/inspect'],
    ['/admin/api/v1/queue/redis?status=ACTIVE', '/queue/api/v1/manage/queue/redis?status=ACTIVE'],
    ['/admin/api/v1/notice/select', '/support/api/v1/manage/notice/select'],
    ['/admin/api/v1/faq/select', '/support/api/v1/manage/faq/select'],
    ['/admin/api/v1/inquiry/select', '/support/api/v1/manage/inquiry/select'],
    ['/admin/api/v1/audit-log/select', '/audit/api/v1/audit-log/select'],
    ['/admin/api/v1/manage/kafka-dlq/topics', '/admin-api/api/v1/manage/kafka-dlq/topics'],
  ];

  for (const [before, after] of cases) {
    await window.Fetch(before);
    const call = calls.at(-1);
    assert.equal(call.url, after);
    assert.equal(call.options.headers.get('Authorization'), 'Bearer admin-token');
  }

  await assert.rejects(() => window.Fetch('https://other.example/api/v1/user/select'), /외부 주소/);
  assert.equal(calls.length, cases.length);

  window.openDashboardEmbedWindow('seatRedis');
  window.openDashboardEmbedWindow('queueRedis');
  assert.deepEqual(messages.map(({ message, origin }) => ({ type: message.type, menu: message.menu, origin })), [
    { type: 'admin:open-embed', menu: 'seatRedis', origin: 'http://localhost' },
    { type: 'admin:open-embed', menu: 'queueRedis', origin: 'http://localhost' },
  ]);
});

test('관리 화면 조각의 정적 파일 경로가 모두 유효하다', async () => {
  const publicRoot = new URL('../public/', import.meta.url);
  const fragmentRoot = new URL('legacy/fragments/', publicRoot);
  const fragments = (await readdir(fragmentRoot)).filter(name => name.endsWith('.html'));
  assert.equal(fragments.length, 26);
  for (const [name, script] of [['seatRedis', 'fragment-redis.js'], ['queueRedis', 'fragment-queue-redis.js']]) {
    const html = await readFile(new URL(`${name}.html`, fragmentRoot), 'utf8');
    assert.match(html, new RegExp(`/admin/legacy/js/redis/${script}`));
  }

  for (const name of fragments) {
    const html = await readFile(new URL(name, fragmentRoot), 'utf8');
    assert.doesNotMatch(html, /\bth:(?:href|src|replace|if|each|class|text|data-url|aria-label)=/);
    for (const match of html.matchAll(/(?:href|src)="(\/admin\/legacy\/(?:css|js)\/[^"?#]+)"/g)) {
      const asset = new URL(match[1].replace(/^\/admin\//, ''), publicRoot);
      await access(asset);
    }
  }
});
