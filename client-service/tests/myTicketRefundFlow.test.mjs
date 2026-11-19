import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import ts from 'typescript';
import { JSDOM } from 'jsdom';
import React, { act } from 'react';

const dom = new JSDOM('<div id="root"></div>', { url: 'http://localhost' });
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
dom.window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
dom.window.HTMLDialogElement.prototype.close = function () { this.open = false; };
const { createRoot } = await import('react-dom/client');
let source = ts.transpileModule(fs.readFileSync(new URL('../src/MyTicketDetailDialog.tsx', import.meta.url), 'utf8'), {
  compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
for (const name of ['react', 'react/jsx-runtime']) source = source.replaceAll(`from "${name}"`, `from '${import.meta.resolve(name)}'`).replaceAll(`from '${name}'`, `from '${import.meta.resolve(name)}'`);
for (const name of ['authenticatedRequest', 'cardPayment']) source = source.replaceAll(`from './${name}'`, `from '${new URL(`../src/${name}.ts`, import.meta.url)}'`);
const Dialog = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default;

function detail(pgPaymentId) {
  return {
    reservation: { reservationId: 1, userId: 'owner', eventId: 1, eventTitle: '공연', eventDateTime: '2099-12-20T18:00:00', venue: '공연장', cancelDeadlineAt: '2099-12-19T18:00:00' },
    tickets: [{ ticketId: 1, seatName: 'A 1열 1번', zone: 'A', seatRow: 1, seatCol: 1, grade: 'VIP', price: 10000, status: 'PAID' }],
    delivery: null,
    payment: { pgPaymentId, paymentNo: 'PAY-1', method: 'CREDIT_CARD', status: 'PAID', amount: 10000, refundedAmount: 0, paidAt: '2026-11-18T12:00:00', expiresAt: null, cardCompany: 'SHINHAN', maskedCardNumber: '4111-****-****-1111', bankName: null, accountNumber: null, depositorName: null },
    totalTicketAmount: 10000, totalDiscountAmount: 0,
  };
}

async function scenario(pgPaymentId) {
  const calls = [];
  let completed = 0;
  document.body.innerHTML = '<div id="root"></div>';
  const root = createRoot(document.getElementById('root'));
  const request = async (url, options) => {
    calls.push({ url, options });
    if (url.includes('/select/detail/')) return detail(pgPaymentId);
    if (options.method === 'POST') return { outcome: 'SUCCEEDED', message: '환불 완료', application: {} };
    return [];
  };
  await act(async () => root.render(React.createElement(Dialog, { reservationId: 1, request, onClose() {}, onRefunded() { completed++; } })));
  if (pgPaymentId) {
    await act(async () => document.querySelector('.my-ticket-seat-table input[type="checkbox"]').click());
    await act(async () => document.querySelector('.my-ticket-seat-heading .my-ticket-refund-button').click());
    await act(async () => [...document.querySelectorAll('button')].find(button => button.textContent === '환불하기').click());
  }
  return { calls, get completed() { return completed; }, close: async () => act(async () => root.unmount()) };
}

test('PG 카드 환불은 신규 환불 신청 API만 호출한다', async () => {
  const run = await scenario('pg-1');
  try {
    assert.equal(run.completed, 1);
    const posts = run.calls.filter(call => call.options.method === 'POST');
    assert.equal(posts.length, 1);
    assert.equal(posts[0].url, '/ticket/api/v1/reservation/1/refunds');
    assert.deepEqual(JSON.parse(posts[0].options.body).selectedTicketIdList, [1]);
    assert.ok(run.calls.every(call => !call.url.includes('/cancel/id/')));
  } finally { await run.close(); }
});

test('PG 거래가 없는 결제는 구형 취소·환불 API로 우회하지 않는다', async () => {
  const run = await scenario(null);
  try {
    assert.equal(run.completed, 0);
    assert.equal(run.calls.filter(call => call.options.method !== 'GET').length, 0);
    assert.equal(document.querySelector('.my-ticket-seat-table input[type="checkbox"]').disabled, true);
    assert.equal(document.querySelector('.my-ticket-refund-button'), null);
  } finally { await run.close(); }
});
