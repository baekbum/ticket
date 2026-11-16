import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import ts from 'typescript';
import { JSDOM } from 'jsdom';
import React, { act } from 'react';
import { ApiRequestError } from '../src/authenticatedRequest.ts';

const dom = new JSDOM('<div id="root"></div>', { url: 'http://localhost' });
globalThis.window = dom.window; globalThis.document = dom.window.document;
globalThis.HTMLElement = dom.window.HTMLElement; globalThis.sessionStorage = dom.window.sessionStorage;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; };
const { createRoot } = await import('react-dom/client');
let source = fs.readFileSync(new URL('../src/CardPaymentDialog.tsx', import.meta.url), 'utf8');
source = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
source = source.replace(/import ['"][^'"]+\.css['"];?/g, '');
for (const name of ['react', 'react/jsx-runtime']) source = source.replaceAll(`from "${name}"`, `from '${import.meta.resolve(name)}'`).replaceAll(`from '${name}'`, `from '${import.meta.resolve(name)}'`);
for (const name of ['authenticatedRequest', 'cardPayment']) source = source.replaceAll(`from './${name}'`, `from '${new URL(`../src/${name}.ts`, import.meta.url)}'`);
const Dialog = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default;
const payment = { paymentNo: 'PAY-1', pgPaymentId: 'pg-1', amount: 10000, expiresAt: '2099-01-01', status: 'READY' };
const session = () => ({ pgPaymentId: 'pg-1', paymentNo: 'PAY-1', amount: 10000, paymentSession: 'opaque-session', expiresAt: new Date(Date.now()+300000).toISOString(), canApprove: true });

test('다른 창에서 무통장으로 바뀌면 카드 조회를 중단하고 현재 결제 확인을 안내한다', async () => {
  let completed = 0;
  const close = await mount(async () => { throw new ApiRequestError(409, '결제수단이 변경되었습니다. 예매 내역을 확인해주세요.', 'GATEWAY_PAYMENT_METHOD_CHANGED'); }, () => { completed++; });
  try {
    assert.equal(completed, 0);
    assert.ok(document.body.textContent.includes('결제수단이 변경되었습니다'));
    assert.ok([...document.querySelectorAll('button')].some(button => button.textContent === '창 닫기'));
    assert.equal(document.querySelector('form'), null);
  } finally { await close(); }
});

test('전환 진행 중 충돌은 승인 없음으로 처리하지 않고 기존 거래 조회를 유지한다', async () => {
  let approved = 0;
  const original = window.setTimeout;
  window.setTimeout = (callback, delay) => delay === 3000 ? 123456 : original(callback, delay);
  const close = await mount(async (url, options) => {
    if (url.startsWith('/ticket/')) return { ...session(), canApprove: false };
    if (options.method === 'POST') approved++;
    throw new ApiRequestError(409, '결제수단 전환을 확인 중입니다. 최초 무통장 전환 요청으로 다시 확인해주세요.');
  }, () => {});
  try {
    assert.equal(approved, 0);
    assert.ok(document.body.textContent.includes('최초 무통장 전환 요청'));
    assert.equal(document.querySelector('form'), null);
  } finally { await close(); window.setTimeout = original; }
});

async function mount(request, onComplete, pending = true) {
  sessionStorage.clear();
  if (pending) sessionStorage.setItem('ticksy.card-attempt:PAY-1', 'PENDING');
  document.body.innerHTML = '<div id="root"></div>';
  const root = createRoot(document.getElementById('root'));
  await act(async () => { root.render(React.createElement(Dialog, { payment, request, onComplete, onClose() {} })); });
  return async () => { await act(async () => root.unmount()); };
}

test('새로고침 후 세션을 메모리에서 다시 받아 PG 거래를 조회하고 통보 완료 후에만 화면을 완료한다', async () => {
  const calls = []; let completed = 0; let delivered = false; let poll;
  const original = window.setTimeout;
  window.setTimeout = (callback, delay) => delay === 3000 ? (poll = callback, 123456) : original(callback, delay);
  const close = await mount(async (url, options) => {
    calls.push({ url, options });
    if (url.startsWith('/ticket/')) return session();
    return { paymentNo: 'PAY-1', pgPaymentId: 'pg-1', pgStatus: 'PAID', notificationStatus: delivered ? 'DELIVERED' : 'PENDING' };
  }, () => { completed++; });
  try {
    assert.equal(completed, 0);
    assert.equal(calls[0].url, '/ticket/api/v1/checkout/payments/PAY-1/gateway-session');
    assert.equal(calls[1].url, '/payment-gateway/api/v1/payments/card/checkout/pg-1');
    assert.equal(calls[1].options.headers['X-PG-Payment-Session'], 'opaque-session');
    assert.equal(JSON.stringify(sessionStorage).includes('opaque-session'), false);
    delivered = true;
    await act(async () => { poll(); });
    assert.equal(completed, 1);
    assert.equal(calls.filter(c => c.options.method === 'POST').length, 1);
    assert.equal(sessionStorage.getItem('ticksy.card-attempt:PAY-1'), null);
  } finally { await close(); window.setTimeout = original; }
});

test('카드 입력값은 Ticksy에 보내지 않고 세션이 있는 PG 승인 API에 한 번만 전달한다', async () => {
  const calls = []; let completed = 0;
  const close = await mount(async (url, options) => {
    calls.push({ url, options });
    if (url.startsWith('/ticket/')) return session();
    return { paymentNo: 'PAY-1', pgPaymentId: 'pg-1', pgStatus: 'PAID', notificationStatus: 'DELIVERED' };
  }, () => { completed++; }, false);
  const button = text => [...document.querySelectorAll('button')].find(b => b.textContent === text);
  async function enter(input, value) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value);
      input.dispatchEvent(new window.Event('input', { bubbles: true }));
    });
  }
  async function submit() { await act(async () => { document.querySelector('form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })); }); }
  try {
    await act(async () => { button('신한카드').click(); }); await submit();
    const inputs = [...document.querySelectorAll('.card-number-segments input')];
    for (let i = 0; i < 4; i++) await enter(inputs[i], i === 0 ? '4111' : '1111');
    await enter(document.querySelector('input[aria-label="카드 번호 1번째 4자리"]') || inputs[0], '4111');
    await enter(document.querySelector('input[placeholder="카드 뒷면의 숫자 3자리"]'), '123'); await submit();
    await enter(document.querySelector('#card-online-password'), 'online-password'); await submit();
    assert.equal(completed, 1);
    const bank = calls.find(c => c.url.endsWith('/approve'));
    assert.ok(bank);
    assert.equal(bank.url, '/payment-gateway/api/v1/payments/card/checkout/pg-1/approve');
    assert.equal(bank.options.headers['X-PG-Payment-Session'], 'opaque-session');
    assert.equal(JSON.parse(bank.options.body).cardNumber, '4111111111111111');
    assert.equal(calls.filter(c => c.url.endsWith('/approve')).length, 1);
    assert.ok(calls.filter(c => c.url.startsWith('/ticket/')).every(c => !c.options.body));
  } finally { await close(); }
});
