import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { URL } from 'node:url';
import { CARD_COMPANIES, PRIMARY_CARD_COMPANIES, OTHER_CARD_COMPANIES, cardResult, formatCardNumber,
  emptyCardPaymentForm, cardPaymentFormReducer, isCardStepValid, buildCardApprovalRequest,
  INVALID_CARD_INFORMATION_MESSAGE, shouldCheckCardApprovalStatus } from '../src/cardPayment.ts';

test('카드사 코드는 백엔드 enum과 일치하고 표시명은 한글이다', () => {
  const java = readFileSync(new URL('../../common/src/main/java/dev/bum/common/service/ticket/payment/enums/CardCompany.java', import.meta.url), 'utf8');
  const codes = java.slice(java.indexOf('{') + 1, java.lastIndexOf('}')).split(',').map((code) => code.trim());
  assert.deepEqual(CARD_COMPANIES.map((card) => card.code), codes);
  assert.ok(CARD_COMPANIES.every((card) => /^[가-힣]+$/.test(card.name)));
});

test('202 대기 및 승인만 된 상태를 결제 완료로 처리하지 않는다', () => {
  for (const status of ['PENDING', 'APPROVED', 'TICKET_PAYMENT_FAILED', 'UNKNOWN']) {
    assert.equal(cardResult({ paymentNo: 'PAY-1', status, approved: true }), 'PENDING');
  }
  assert.equal(cardResult({ paymentNo: 'PAY-1', approved: true }), 'COMPLETE');
  assert.equal(cardResult({ paymentNo: 'PAY-1', status: 'TICKET_PAYMENT_COMPLETED' }), 'COMPLETE');
  for (const status of ['CANCELLED', 'APPROVAL_FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED']) {
    assert.equal(cardResult({ paymentNo: 'PAY-1', status, approved: true }), 'TERMINAL');
  }
});

test('붙여넣은 카드번호에서 숫자 16자리만 남기고 네 자리씩 구분한다', () => {
  assert.equal(formatCardNumber('4111 1111-1111abc111122'), '4111-1111-1111-1111');
  assert.equal(formatCardNumber('41111'), '4111-1');
});

test('마지막 지정 목록의 9개 버튼과 기타 7개가 중복 없이 전체 enum을 구성한다', () => {
  assert.deepEqual(PRIMARY_CARD_COMPANIES.map((card) => card.code),
    ['SHINHAN', 'KB', 'SAMSUNG', 'HYUNDAI', 'LOTTE', 'KAKAO', 'TOSS', 'NH', 'WOORI']);
  const all = [...PRIMARY_CARD_COMPANIES, ...OTHER_CARD_COMPANIES].map((card) => card.code);
  assert.equal(new Set(all).size, CARD_COMPANIES.length);
  assert.equal(all.length, CARD_COMPANIES.length);
  assert.equal(OTHER_CARD_COMPANIES.length, 7);
});

test('버튼과 기타 드롭다운 선택은 마지막 카드사 하나만 유지한다', () => {
  let form = emptyCardPaymentForm();
  assert.equal(isCardStepValid(1, form), false);
  for (const code of ['SHINHAN', 'BC', 'KAKAO', 'HANA']) {
    form = cardPaymentFormReducer(form, { type: 'company', value: code });
    assert.equal(form.company, code);
    const selectedButtons = PRIMARY_CARD_COMPANIES.filter((card) => card.code === form.company);
    const selectedOther = OTHER_CARD_COMPANIES.filter((card) => card.code === form.company);
    assert.equal(selectedButtons.length + selectedOther.length, 1);
    assert.equal(isCardStepValid(1, form), true);
  }
  form = cardPaymentFormReducer(form, { type: 'company', value: '' });
  assert.equal(isCardStepValid(1, form), false);
});

test('카드번호 4칸과 CVC가 완성된 경우에만 2단계를 통과한다', () => {
  let form = cardPaymentFormReducer(emptyCardPaymentForm(), { type: 'company', value: 'TOSS' });
  for (let index = 0; index < 4; index++) {
    form = cardPaymentFormReducer(form, { type: 'number', index, value: '12a345' });
    assert.equal(form.cardNumber[index], '1234');
    assert.equal(isCardStepValid(2, form), false);
  }
  form = cardPaymentFormReducer(form, { type: 'cvc', value: 'a12' });
  assert.equal(isCardStepValid(2, form), false);
  form = cardPaymentFormReducer(form, { type: 'cvc', value: '1a234' });
  assert.equal(form.cvc, '123');
  assert.equal(isCardStepValid(2, form), true);
  form = cardPaymentFormReducer(form, { type: 'number', index: 2, value: '' });
  assert.equal(isCardStepValid(2, form), false);
});

test('16자리 붙여넣기는 어느 칸에서든 분배되고 부분 붙여넣기는 해당 칸부터 채운다', () => {
  let form = cardPaymentFormReducer(emptyCardPaymentForm(), { type: 'pasteNumber', index: 2, value: '4111-2222 3333-4444' });
  assert.deepEqual(form.cardNumber, ['4111', '2222', '3333', '4444']);
  form = cardPaymentFormReducer(form, { type: 'pasteNumber', index: 1, value: '5555-66' });
  assert.deepEqual(form.cardNumber, ['4111', '5555', '66', '4444']);
});

function validForm() {
  return { company: 'SHINHAN', cardNumber: ['4111', '1111', '1111', '1111'], cvc: '123', password: 'Online-test!2026' };
}

test('온라인 비밀번호는 숫자 4자리로 제한하지 않고 빈 문자열과 공백은 거부한다', () => {
  const form = validForm();
  for (const password of ['', '   ', 'a'.repeat(65)]) assert.equal(isCardStepValid(3, { ...form, password }), false);
  for (const password of ['1234', 'Online-test!2026', '테스트비밀번호!']) {
    assert.equal(isCardStepValid(3, { ...form, password }), true);
  }
  assert.equal(isCardStepValid(3, { ...form, cvc: '12' }), false);
});

test('카드사 변경으로 돌아가도 다른 정보는 유지하며 닫기 초기화는 전체 값을 비운다', () => {
  const original = validForm();
  const changed = cardPaymentFormReducer(original, { type: 'company', value: 'BC' });
  assert.deepEqual(changed.cardNumber, original.cardNumber);
  assert.equal(changed.cvc, original.cvc);
  assert.equal(changed.password, original.password);
  assert.deepEqual(cardPaymentFormReducer(changed, { type: 'reset' }), emptyCardPaymentForm());
  assert.equal(original.company, 'SHINHAN');
});

test('최종 결제 요청은 기존 DTO 필드와 서버 금액을 사용하고 카드번호는 합쳐서 전달한다', () => {
  const form = validForm();
  const payment = { paymentNo: 'PAY-TEST', amount: 180000 };
  const body = buildCardApprovalRequest(form, payment);
  const dto = readFileSync(new URL('../../payment-gateway-service/src/main/java/dev/bum/payment_gateway_service/dto/card/GatewayCardPaymentApproveRequest.java', import.meta.url), 'utf8');
  const fields = [...dto.matchAll(/private\s+\w+\s+(\w+);/g)].map((match) => match[1]).sort();
  assert.deepEqual(Object.keys(body).sort(), fields);
  assert.equal(body.cardNumber, form.cardNumber.join(''));
  assert.match(body.cardNumber, /^\d{16}$/);
  assert.equal(body.cardPassword, form.password);
  assert.equal('customerName' in body, false);
  assert.equal(body.amount, payment.amount);
  assert.equal(body.paymentNo, payment.paymentNo);
  assert.throws(() => buildCardApprovalRequest(emptyCardPaymentForm(), payment));
});

test('카드 정보 오류는 일반 문구로 표시하고 승인 상태를 조회하지 않는다', () => {
  const wrongPassword = Object.assign(new Error('카드 비밀번호가 일치하지 않습니다.'), { status: 400 });
  assert.equal(INVALID_CARD_INFORMATION_MESSAGE, '정보가 올바르지 않습니다.');
  assert.equal(shouldCheckCardApprovalStatus(wrongPassword), false);
  assert.equal(shouldCheckCardApprovalStatus(Object.assign(new Error('응답 유실'), { status: 502 })), true);
  assert.equal(shouldCheckCardApprovalStatus(new Error('네트워크 오류')), true);
});
