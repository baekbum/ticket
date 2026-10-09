import assert from 'node:assert/strict';
import test from 'node:test';
import { JSDOM } from 'jsdom';

const dom = new JSDOM('<!doctype html><body></body>', { runScripts: 'dangerously' });
globalThis.window = dom.window;
const { sanitizeLayoutSvg } = await import('../src/sanitizeLayoutSvg.ts');

test('malicious SVG cannot execute when inserted into the page', () => {
  dom.window.attacked = 0;
  const clean = sanitizeLayoutSvg(`<svg xmlns="http://www.w3.org/2000/svg" onload="window.attacked++">
    <script>window.attacked++</script><style>@import url(https://evil.test)</style>
    <foreignObject><img src="x" onerror="window.attacked++"></foreignObject>
    <a href="javascript:window.attacked++"><text>attack</text></a>
    <image href="https://evil.test/tracker"/><use href="https://evil.test/image.svg#x"/>
    <rect class="area vip" data-layout-key="VIP" onclick="window.attacked++" style="fill:url(https://evil.test)">
      <animate attributeName="href" values="javascript:window.attacked++"/>
    </rect></svg>`);
  const host = dom.window.document.createElement('div');
  host.innerHTML = clean;
  dom.window.document.body.append(host);
  host.querySelector('svg').dispatchEvent(new dom.window.Event('load'));
  host.querySelector('rect').dispatchEvent(new dom.window.Event('click'));
  assert.equal(dom.window.attacked, 0);
  assert.equal(host.querySelector('script, style, foreignObject, a, image, use, animate'), null);
  assert.doesNotMatch(clean, /onload|onclick|javascript:|evil\.test|style=/);
  host.remove();
});

test('geometry, gradients and area interaction attributes survive sanitization', () => {
  const clean = sanitizeLayoutSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 420 180">
    <defs><linearGradient id="paint"><stop offset="0%" stop-color="#fff"/></linearGradient></defs>
    <rect id="area-vip" class="area vip booking-area-shape is-selected" data-layout-key="VIP"
      data-area-name="VIP A" data-area-id="42" data-price="165000" data-grade="VIP" tabindex="0"
      role="button" aria-label="VIP A 구역 선택" x="20" y="20" width="180" height="120" fill="url(#paint)">
      <title>VIP A · 165,000원</title></rect></svg>`);
  const host = dom.window.document.createElement('div');
  host.innerHTML = clean;
  const area = host.querySelector('[data-area-id="42"]');
  assert.ok(area);
  assert.equal(area.getAttribute('data-layout-key'), 'VIP');
  assert.equal(area.getAttribute('role'), 'button');
  assert.equal(area.getAttribute('tabindex'), '0');
  assert.equal(area.getAttribute('aria-label'), 'VIP A 구역 선택');
  assert.equal(area.getAttribute('fill'), 'url(#paint)');
  assert.equal(host.querySelector('svg').getAttribute('viewBox'), '0 0 420 180');
  assert.ok(host.querySelector('linearGradient'));
  assert.equal(area.querySelector('title').textContent, 'VIP A · 165,000원');
  assert.equal(sanitizeLayoutSvg(clean), clean);
});

test('allowed text remains escaped while forbidden wrapper content is discarded', () => {
  const clean = sanitizeLayoutSvg(`<svg xmlns="http://www.w3.org/2000/svg">
    <text>&lt;img src=x onerror=alert(1)&gt;</text>
    <a href="javascript:alert(1)"><text>discarded link content</text></a>
  </svg>`);
  const host = dom.window.document.createElement('div');
  host.innerHTML = clean;
  assert.equal(host.querySelector('img, a'), null);
  assert.equal(host.querySelector('text').textContent, '<img src=x onerror=alert(1)>');
  assert.doesNotMatch(clean, /discarded link content/);
});

test('external paint references, namespaced handlers and SVG animation are removed', () => {
  for (const value of ['url(https://evil.test/x)', 'url(//evil.test/x)', 'url(data:image/svg+xml,x)', 'url(javascript:alert(1))']) {
    const clean = sanitizeLayoutSvg(`<svg xmlns="http://www.w3.org/2000/svg"><path fill="${value}" stroke="${value}" clip-path="${value}"/></svg>`);
    assert.doesNotMatch(clean, /fill=|stroke=|clip-path=/);
  }
  const clean = sanitizeLayoutSvg(`<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
    <set attributeName="onload" to="alert(1)"/><svg xmlns="http://www.w3.org/1999/xhtml"><script>alert(1)</script></svg>
    <path xlink:href="javascript:alert(1)" onpointerenter="alert(1)"/></svg>`);
  assert.doesNotMatch(clean, /<set|<script|onpointerenter|javascript:/);
});

test('flattened layout colors, labels, fonts and click-through survive repeated sanitization', () => {
  const clean = sanitizeLayoutSvg(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 850 870">
    <rect class="bg" width="850" height="870" fill="#cfe7f4"/>
    <text class="title" fill="#69a8d1" font-size="38px" font-weight="700" font-family="Arial, sans-serif">좌석 배치도</text>
    <rect class="area vip" data-layout-key="A" fill="#456bd7"/>
    <text class="label" fill="#fff" font-size="15px" text-anchor="middle" pointer-events="none">A</text>
  </svg>`);
  const host = dom.window.document.createElement('div');
  host.innerHTML = sanitizeLayoutSvg(clean);
  assert.equal(host.querySelector('.bg').getAttribute('fill'), '#cfe7f4');
  assert.equal(host.querySelector('.title').getAttribute('font-size'), '38px');
  assert.equal(host.querySelector('.title').textContent, '좌석 배치도');
  assert.equal(host.querySelector('.label').getAttribute('fill'), '#fff');
  assert.equal(host.querySelector('.label').getAttribute('pointer-events'), 'none');
  assert.equal(host.querySelector('.area').getAttribute('data-layout-key'), 'A');
  assert.equal(sanitizeLayoutSvg(clean), clean);
});
