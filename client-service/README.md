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

## 로컬 실행

```bash
npm install
npm run dev
```

- 개발 서버: `http://localhost:3000`
- API 프록시
  - `/auth/*` → `http://localhost:8080`
  - `/user/*` → `http://localhost:8081`
  - `/ticket/*` → `http://localhost:8082`
  - `/queue/*` → `http://localhost:8083`

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
