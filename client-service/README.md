# client-service

React 기반 사용자 클라이언트 서비스입니다.

## 무통장 입금 예매

- 예매 팝업의 결제 단계에서 무통장 입금과 은행을 선택하고 약관 동의 후 결제합니다.
- 은행 선택지는 `common`의 `BankCompany` 코드/표시명과 일치해야 합니다.
- `/client-api/api/v1/checkout/confirm` → ticket-service의 `/api/v1/checkout/confirm` → payment-gateway-service의 `/api/v1/payments/virtual-account/issue` 순서로 호출합니다.
- `prepare`의 `idempotencyKey`를 재시도에도 유지합니다. 현장 수령은 `delivery: null`, 배송은 배송지 객체를 전달합니다.
- 발급 금액은 좌석 금액에서 쿠폰 할인을 적용한 뒤 서버 설정의 예매 수수료와 배송비를 더합니다.
- 발급 성공 시 예매 완료 화면에 은행, 계좌번호, 실제 입금 금액, 입금 기한을 표시합니다. 입금 전 상태는 `WAITING_DEPOSIT`입니다.
- 신용카드 승인 연동은 아직 구현되지 않았습니다.

수동 확인: 은행 미선택/미동의 시 요청 차단, 현장 수령 및 배송 각각의 발급 금액,
연속 클릭 시 단일 요청, 발급 실패 후 재시도, 완료 후 제한시간 경과 시 화면 유지를 확인합니다.

## 예매 팝업의 토큰과 재시도

- 새로고침 및 일반 통신 오류에서는 active-token을 반환하거나 팝업을 닫지 않습니다. 예매 완료 시 반환하고, 창을 그냥 닫은 경우에는 서버 TTL로 정리합니다.
- access-token 만료로 401을 받으면 동시에 발생한 요청들이 하나의 refresh를 공유하며, 갱신된 토큰으로 원래 본문과 `X-Active-Token`을 유지해 한 번 재전송합니다.
- refresh 중 통신 장애 또는 5xx가 발생하면 기존 토큰을 유지하여 재시도할 수 있습니다. refresh 인증 자체가 거부되면 다시 로그인이 필요합니다.
- 서버가 `ACTIVE_TOKEN_EXPIRED`를 반환하거나 화면의 선택 시간이 끝나면, 선택 시간 초과 안내의 확인 버튼을 눌렀을 때 팝업을 닫습니다. 대기열 검증 서버의 통신 장애는 `QUEUE_UNAVAILABLE` / 503이며 토큰 만료와 구분합니다.
- 결제 확정도 `X-Active-Token`을 검증합니다. 이미 처리된 동일한 결제 요청은 기존 결과를 반환하여 중복 결제를 방지합니다.
- 인증 요청 회귀 테스트: `node --test tests/authenticatedRequest.test.mjs tests/apiErrorMessage.test.mjs` (Node.js 24).

## 로컬 실행

```bash
npm install
npm run dev
```

- 개발 서버: `http://localhost:3000`
- API 프록시
  - `/client-api/*` → `http://localhost:8090`
  - `/payment-gateway/*` → `http://localhost:8099`

사용자용 티켓 API는 프론트에서 ticket-service로 직접 요청하지 않고 client-api BFF를 통해 호출합니다.

## 빌드

```bash
npm run build
npm run preview
```

## Docker

```bash
docker build -t client-service ./client-service
docker run --rm -p 3000:3000 client-service
```
