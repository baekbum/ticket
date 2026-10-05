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
  assert.equal(sanitizeLayoutSvg(clean), clean);
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
