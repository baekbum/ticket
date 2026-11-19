import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import ts from 'typescript';
import { JSDOM } from 'jsdom';
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { ApiRequestError } from '../src/authenticatedRequest.ts';

let source = fs.readFileSync(new URL('../src/MyTicketDetailDialog.tsx', import.meta.url), 'utf8');
source = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
for (const name of ['react', 'react/jsx-runtime']) source = source.replaceAll(`from "${name}"`, `from '${import.meta.resolve(name)}'`).replaceAll(`from '${name}'`, `from '${import.meta.resolve(name)}'`);
for (const name of ['authenticatedRequest', 'cardPayment']) source = source.replaceAll(`from './${name}'`, `from '${new URL(`../src/${name}.ts`, import.meta.url)}'`);
const Dialog = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default;

function detail() {
  return { reservation: { reservationId: 1, userId: 'owner', eventId: 1, eventTitle: '공연', eventDateTime: '2026-12-01', venue: '공연장', cancelDeadlineAt: '2026-11-30' },
    tickets: [1, 2].map((id) => ({ ticketId: id, seatName: `A ${id}번`, zone: 'A', seatRow: 1, seatCol: id, grade: 'VIP', price: 10000, status: 'PAID' })),
    payment: { pgPaymentId: 'pg-1', paymentNo: 'pay-1', method: 'BANK_TRANSFER', status: 'PAID', amount: 20000, refundedAmount: 0 },
    delivery: null, totalTicketAmount: 20000, totalDiscountAmount: 0 };
}
const application = (status = 'REQUESTED', id = 99) => ({ paymentRefundProcessId: id, selectedTicketIds: '1', refundAmount: 10000, cancellationFeeAmount: 0, status, refundBankCompany: 'KB', refundAccountNumberMasked: '******7890', createdAt: '2026-11-13' });

async function mount(request) {
  const dom = new JSDOM('<div id="root"></div>', { url: 'http://localhost' });
  globalThis.window = dom.window; globalThis.document = dom.window.document;
  globalThis.HTMLElement = dom.window.HTMLElement; globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; };
  dom.window.confirm = () => true;
  const root = createRoot(dom.window.document.getElementById('root'));
  await act(async () => { root.render(React.createElement(Dialog, { reservationId: 1, request, onClose() {} })); });
  return { document: dom.window.document, async close() { await act(async () => root.unmount()); dom.window.close(); } };
}
const button = (document, text) => [...document.querySelectorAll('button')].find((item) => item.textContent === text);

test('환불 재시도·신청 취소는 미완료 신청에만 표시한다', async () => {
  const view = await mount(async (url) => url.endsWith('/refunds') ? [application(), application('LOCAL_SUCCEEDED', 100), application('WITHDRAWN', 101)] : detail());
  try {
    assert.equal([...view.document.querySelectorAll('button')].filter((item) => item.textContent === '다시 시도').length, 1);
    assert.equal([...view.document.querySelectorAll('button')].filter((item) => item.textContent === '환불 신청 취소').length, 1);
    assert.ok(view.document.body.textContent.includes('******7890'));
  } finally { await view.close(); }
});

test('환불 재시도 실패 시 같은 신청을 한 번만 요청하고 안내한다', async () => {
  const calls = [];
  const view = await mount(async (url, options) => {
    if (options.method === 'POST') { calls.push(url); throw new ApiRequestError(503, '같은 신청으로 다시 시도해주세요.', 'GATEWAY_REFUND_UNCONFIRMED'); }
    return url.endsWith('/refunds') ? [application()] : detail();
  });
  try {
    await act(async () => { button(view.document, '다시 시도').click(); });
    assert.deepEqual(calls, ['/ticket/api/v1/reservation/1/refunds/99/retry']);
    assert.ok(view.document.body.textContent.includes('같은 신청으로 다시 시도해주세요.'));
  } finally { await view.close(); }
});

test('철회 요청에서 환불 완료를 복구하면 결과를 안내하고 신청 버튼을 숨긴다', async () => {
  let completed = false;
  const calls = [];
  const message = '이미 환불이 완료된 신청입니다. 환불 결과를 반영했습니다.';
  const view = await mount(async (url, options) => {
    if (options.method === 'POST') { calls.push(url); completed = true; return { outcome: 'SUCCEEDED', application: application('LOCAL_SUCCEEDED'), message }; }
    return url.endsWith('/refunds') ? [application(completed ? 'LOCAL_SUCCEEDED' : 'REQUESTED')] : detail();
  });
  try {
    await act(async () => { button(view.document, '환불 신청 취소').click(); });
    assert.deepEqual(calls, ['/ticket/api/v1/reservation/1/refunds/99/withdraw']);
    assert.ok(view.document.body.textContent.includes(message));
    assert.equal(button(view.document, '환불 신청 취소'), undefined);
    assert.equal(button(view.document, '다시 시도'), undefined);
  } finally { await view.close(); }
});

test('신규 PG 카드는 계좌 입력 없이 공통 신청 API로 환불한다', async () => {
  const payload = detail(); payload.payment.method = 'CREDIT_CARD';
  const calls = [];
  const view = await mount(async (url, options) => {
    if (options.method === 'POST') {
      calls.push({ url, body: JSON.parse(options.body) });
      return { outcome: 'SUCCEEDED', application: application('LOCAL_SUCCEEDED'), message: '환불이 완료되었습니다.' };
    }
    return url.endsWith('/refunds') ? [] : payload;
  });
  try {
    assert.equal(button(view.document, '환불 신청'), undefined);
    assert.equal(view.document.querySelectorAll('.my-ticket-seat-table button').length, 0);
    const checkboxes = view.document.querySelectorAll('.my-ticket-seat-table input[type="checkbox"]');
    await act(async () => { checkboxes[0].click(); });
    assert.ok(view.document.querySelector('.my-ticket-seat-heading .my-ticket-refund-button'));
    await act(async () => { checkboxes[0].click(); });
    assert.equal(button(view.document, '환불 신청'), undefined);
    await act(async () => { checkboxes[0].click(); checkboxes[1].click(); });
    await act(async () => { button(view.document, '환불 신청').click(); });
    assert.ok(!view.document.body.textContent.includes('환불 계좌 정보'));
    await act(async () => { button(view.document, '환불하기').click(); });
    assert.deepEqual(calls, [{ url: '/ticket/api/v1/reservation/1/refunds',
      body: { eventId: 1, selectedTicketIdList: [1, 2], refundAccount: null } }]);
    assert.ok(view.document.body.textContent.includes('환불이 완료되었습니다.'));
  } finally { await view.close(); }
});

test('신규 PG 카드도 미완료 신청의 재시도와 철회 버튼을 표시한다', async () => {
  const payload = detail(); payload.payment.method = 'CREDIT_CARD';
  const view = await mount(async (url) => url.endsWith('/refunds')
    ? [application(), application('LOCAL_SUCCEEDED', 100)] : payload);
  try {
    assert.equal([...view.document.querySelectorAll('button')].filter((item) => item.textContent === '다시 시도').length, 1);
    assert.equal([...view.document.querySelectorAll('button')].filter((item) => item.textContent === '환불 신청 취소').length, 1);
    assert.ok(view.document.body.textContent.includes('결제한 카드로 취소·환불'));
    assert.ok(!view.document.body.textContent.includes('******7890'));
  } finally { await view.close(); }
});

test('무통장 환불 선택을 모두 해제하면 신청 버튼과 기존 계좌 입력을 숨긴다', async () => {
  const posts = [];
  const view = await mount(async (url, options) => {
    if (options.method === 'POST') posts.push(url);
    return url.endsWith('/refunds') ? [] : detail();
  });
  try {
    const checkbox = view.document.querySelector('.my-ticket-seat-table input[type="checkbox"]');
    assert.equal(button(view.document, '환불 신청'), undefined);
    await act(async () => { checkbox.click(); });
    await act(async () => { view.document.querySelector('.my-ticket-seat-heading button').click(); });
    assert.ok(view.document.querySelector('.my-ticket-refund-form'));
    await act(async () => { checkbox.click(); });
    assert.equal(button(view.document, '환불 신청'), undefined);
    assert.equal(view.document.querySelector('.my-ticket-refund-form'), null);
    assert.deepEqual(posts, []);
  } finally { await view.close(); }
});
