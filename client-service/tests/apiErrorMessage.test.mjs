import assert from 'node:assert/strict';
import test from 'node:test';
import { getApiErrorMessage } from '../src/apiErrorMessage.ts';

test('빈 응답, 표준 오류, 기존 HTTP 코드 문구를 사용자 안내로 변환한다', () => {
  for (const body of ['', 'Forbidden', '{"error":"Forbidden"}', '요청 실패: 403', 'null', '[]']) {
    assert.equal(getApiErrorMessage(403, body), '처리 도중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.');
  }
});

test('업무 오류 코드와 구체적인 한국어 안내는 유지한다', () => {
  assert.match(getApiErrorMessage(403, '{"code":"QUEUE_ACCESS_DENIED"}'), /다시 입장/);
  assert.match(getApiErrorMessage(409, '{"code":"SEAT_ALREADY_OCCUPIED"}'), /다른 좌석/);
  const message = '공연 당일에는 무통장 입금을 사용할 수 없습니다.';
  assert.equal(getApiErrorMessage(400, JSON.stringify({ message })), message);
  assert.equal(getApiErrorMessage(400, JSON.stringify({ code: 'INVALID_REQUEST', message })), message);
  assert.equal(getApiErrorMessage(400, message), message);
});

test('서버 장애나 기술적인 응답을 노출하지 않는다', () => {
  for (const body of ['<html>오류</html>', '{"message":"DB 오류: SQLException"}', '{"message":{"detail":"오류"}}']) {
    assert.match(getApiErrorMessage(503, body), /일시적인 서비스 오류/);
    assert.equal(getApiErrorMessage(400, body), '입력한 정보를 확인한 후 다시 시도해주세요.');
  }
});

test('주요 HTTP 상태와 알 수 없는 상태에도 안내 문구를 제공한다', () => {
  assert.match(getApiErrorMessage(401, ''), /다시 로그인/);
  assert.match(getApiErrorMessage(404, ''), /찾을 수 없습니다/);
  assert.match(getApiErrorMessage(429, ''), /요청이 많아/);
  assert.match(getApiErrorMessage(502, ''), /일시적인 서비스 오류/);
  assert.match(getApiErrorMessage(499, '{"code":"UNKNOWN_ERROR"}'), /요청을 완료하지 못했습니다/);
});
