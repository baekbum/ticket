import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import ts from 'typescript';
import { JSDOM } from 'jsdom';
import React, { act } from 'react';
import { ApiRequestError } from '../src/authenticatedRequest.ts';

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
  await act(async () => root.render(React.createElement(Dialog, { reservationId: 1, request, onClose() {}, onReservationChanged() { completed++; } })));
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

function unpaidDetail() {
  const value = detail('pg-2');
  value.reservation.reservationId = 2;
  value.payment = { ...value.payment, paymentNo: 'PAY-2', method: 'BANK_TRANSFER', status: 'WAITING_DEPOSIT',
    amount: 20000, paidAt: null, expiresAt: '2099-12-19T23:59:59', bankName: '우리은행', accountNumber: '3333-0000-000000' };
  value.tickets = [1, 2].map((id) => ({ ...value.tickets[0], ticketId: id, seatCol: id,
    seatName: `A 1열 ${id}번`, status: 'PENDING_PAYMENT' }));
  value.totalTicketAmount = 20000;
  return value;
}

function cancelledDetail(value) {
  return { ...value, payment: { ...value.payment, status: 'CANCELLED' },
    tickets: value.tickets.map((ticket) => ({ ...ticket, status: 'CANCELLED' })) };
}

const findButton = (label) => [...document.querySelectorAll('button')].find((button) => button.textContent === label);
const cancellationCheckboxes = () => [...document.querySelectorAll('.my-ticket-seat-table input[type="checkbox"]')];

async function openCancellationConfirmation() {
  await act(async () => cancellationCheckboxes()[0].click());
  await act(async () => findButton('취소 신청').click());
}

async function cancellationScenario(initial = unpaidDetail(), send) {
  const calls = [];
  let current = initial;
  let changed = 0;
  let closed = 0;
  document.body.innerHTML = '<div id="root"></div>';
  const root = createRoot(document.getElementById('root'));
  const request = async (url, options) => {
    calls.push({ url, options });
    if (url.includes('/select/detail/')) return structuredClone(current);
    if (options.method === 'PUT') {
      if (send) return send({ update(value) { current = value; } });
      current = cancelledDetail(current);
      return { reservationId: 2, cancelledTicketIds: [1, 2], message: '입금 전 예매 취소가 완료되었습니다.' };
    }
    return [];
  };
  await act(async () => root.render(React.createElement(Dialog, { reservationId: 2, request,
    onClose() { closed++; }, onReservationChanged() { changed++; } })));
  return { calls, get changed() { return changed; }, get closed() { return closed; },
    async close() { await act(async () => root.unmount()); } };
}

test('입금 대기 예매의 좌석은 함께 선택·해제되며 확인 전에는 취소하지 않는다', async () => {
  const run = await cancellationScenario();
  try {
    const checkboxes = cancellationCheckboxes();
    assert.equal(document.querySelector('.my-ticket-seat-table th:last-child').textContent, '취소');
    assert.equal(findButton('취소 신청'), undefined);
    assert.ok(checkboxes.every((input) => !input.disabled));
    await act(async () => checkboxes[0].click());
    assert.ok(checkboxes.every((input) => input.checked));
    assert.ok(findButton('취소 신청'));
    await act(async () => checkboxes[1].click());
    assert.ok(checkboxes.every((input) => !input.checked));
    assert.equal(findButton('취소 신청'), undefined);
    await openCancellationConfirmation();
    assert.ok(document.getElementById('cancel-confirm-message').textContent.includes('2매를 모두 취소'));
    assert.equal(run.calls.filter((call) => call.options.method === 'PUT').length, 0);
    await act(async () => findButton('돌아가기').click());
    assert.equal(document.getElementById('cancel-confirm-title'), null);
    assert.equal(run.calls.filter((call) => call.options.method !== 'GET').length, 0);
  } finally { await run.close(); }
});

test('입금 전 취소는 전체 티켓을 기존 취소 API에 한 번 보내고 상세·목록을 갱신한다', async () => {
  const run = await cancellationScenario();
  try {
    await openCancellationConfirmation();
    await act(async () => findButton('예매 취소하기').click());
    const writes = run.calls.filter((call) => call.options.method !== 'GET');
    assert.equal(writes.length, 1);
    assert.equal(writes[0].url, '/ticket/api/v1/reservation/cancel/id/2');
    assert.equal(writes[0].options.method, 'PUT');
    assert.deepEqual(JSON.parse(writes[0].options.body), { eventId: 1, selectedTicketIdList: [1, 2] });
    assert.equal(run.changed, 1);
    assert.equal(run.calls.filter((call) => call.url.includes('/select/detail/')).length, 2);
    assert.equal(findButton('취소 신청'), undefined);
    assert.ok(document.body.textContent.includes('입금 전 예매 취소가 완료되었습니다.'));
  } finally { await run.close(); }
});

test('취소 처리 중 연속 확정과 닫기를 막고 같은 요청을 중복 전송하지 않는다', async () => {
  let release;
  const pending = new Promise((resolve) => { release = resolve; });
  const run = await cancellationScenario(unpaidDetail(), async ({ update }) => {
    await pending;
    update(cancelledDetail(unpaidDetail()));
    return { message: '입금 전 예매 취소가 완료되었습니다.' };
  });
  try {
    await openCancellationConfirmation();
    await act(async () => { const confirm = findButton('예매 취소하기'); confirm.click(); confirm.click(); });
    assert.equal(run.calls.filter((call) => call.options.method === 'PUT').length, 1);
    assert.equal(findButton('처리 중…').disabled, true);
    assert.equal(document.querySelector('[aria-label="예매 상세 닫기"]').disabled, true);
    await act(async () => document.querySelector('dialog').dispatchEvent(new window.Event('cancel', { cancelable: true })));
    assert.equal(run.closed, 0);
    await act(async () => release());
    assert.equal(run.changed, 1);
  } finally { release(); await run.close(); }
});

test('입금과 경합해 취소가 거절되면 최신 결제 상태를 조회하고 환불을 자동 실행하지 않는다', async () => {
  const run = await cancellationScenario(unpaidDetail(), async ({ update }) => {
    const paid = unpaidDetail();
    paid.payment.status = 'PAID';
    paid.tickets = paid.tickets.map((ticket) => ({ ...ticket, status: 'PAID' }));
    update(paid);
    throw new ApiRequestError(409, '입금이 완료되어 입금 전 예매 취소를 할 수 없습니다.');
  });
  try {
    await openCancellationConfirmation();
    await act(async () => findButton('예매 취소하기').click());
    assert.ok(document.body.textContent.includes('입금이 완료되어'));
    assert.equal(findButton('취소 신청'), undefined);
    assert.ok(cancellationCheckboxes().every((input) => !input.checked));
    assert.ok([...document.querySelectorAll('input[type="checkbox"]')].every((input) => !input.disabled));
    assert.equal(run.calls.filter((call) => call.options.method === 'POST').length, 0);
    assert.equal(run.changed, 1);
  } finally { await run.close(); }
});

test('취소 응답이 유실되어도 상태 재조회로 취소 완료를 표시하고 자동 재전송하지 않는다', async () => {
  const run = await cancellationScenario(unpaidDetail(), async ({ update }) => {
    update(cancelledDetail(unpaidDetail()));
    throw new Error('network failure');
  });
  try {
    await openCancellationConfirmation();
    await act(async () => findButton('예매 취소하기').click());
    assert.equal(findButton('취소 신청'), undefined);
    assert.equal(run.calls.filter((call) => call.options.method === 'PUT').length, 1);
    assert.equal(run.changed, 1);
    assert.ok(document.body.textContent.includes('예매 취소 결과를 확인하지 못했습니다.'));
  } finally { await run.close(); }
});

test('취소·만료 및 상태가 맞지 않는 거래는 체크해도 취소를 신청할 수 없다', async () => {
  for (const [method, status, ticketStatus, pgPaymentId] of [
    ['BANK_TRANSFER', 'CANCELLED', 'CANCELLED', 'pg-1'], ['BANK_TRANSFER', 'EXPIRED', 'EXPIRED', 'pg-1'],
    ['BANK_TRANSFER', 'WAITING_DEPOSIT', 'PAID', 'pg-1'], ['BANK_TRANSFER', 'WAITING_DEPOSIT', 'PENDING_PAYMENT', null],
  ]) {
    const value = unpaidDetail();
    value.payment = { ...value.payment, method, status, pgPaymentId };
    value.tickets = value.tickets.map((ticket) => ({ ...ticket, status: ticketStatus }));
    const run = await cancellationScenario(value);
    try {
      assert.ok(cancellationCheckboxes().every((input) => input.disabled));
      await act(async () => cancellationCheckboxes()[0].click());
      assert.equal(findButton('취소 신청'), undefined);
      assert.equal(run.calls.filter((call) => call.options.method !== 'GET').length, 0);
    } finally { await run.close(); }
  }
});
