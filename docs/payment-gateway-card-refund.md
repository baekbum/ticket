# 신규 PG 카드 취소·전액·부분 환불

2026-11-15 신규 PG 카드를 기존 Ticksy 환불 신청·재시도·철회 흐름에 연결했다. 카드 원문이나 환불 계좌를 다시 받지 않고 원승인 카드로 환불한다.

## 호출과 트랜잭션

```text
브라우저 → Ticksy ReservationRefundController
  [Ticksy 준비 TX] 예매 NOWAIT 잠금 → 티켓·금액·수수료 스냅샷과 신청 ID 저장·커밋
  [Ticksy 실행 TX] 예매 NOWAIT 잠금 → 최초 신청 조건으로 PG HTTP 1회
PG GatewayPaymentRefundController
  가맹점 인증 → 소유 원거래 조회 → 저장된 결제수단으로 은행/카드 서비스 선택
  [PG 준비 TX] 원거래 잠금 → 최초 환불 조건 저장·커밋
PG → CardController HTTP 환불 또는 신청 철회, 최대 4회
  [카드 TX] 원승인 잠금 → 카드 사용액 복원·누적 환불·개별 금융 결과 저장
  [PG 확정 TX] 원거래 잠금 → PG 환불액·상태·처리 이력 확정
Ticksy
  같은 실행 TX에서 환불 완료·예매·선택 티켓 취소·좌석 AVAILABLE 복원·커밋
브라우저 ← 동기 처리 결과
```

PG는 HTTP 전후의 짧은 트랜잭션을 사용한다. Ticksy는 사용자와 합의한 기존 방식대로 실행 중 예매 NOWAIT 잠금을 유지한다. 같은 예매의 환불·철회가 경합하면 즉시 409로 안내한다. 최초 신청은 별도 커밋돼 있어 실행 실패에도 동일 키로 재시도할 수 있다. 카드사는 원승인 → 카드 원장 순서로 잠가 승인 보상·환불의 중복 한도 복원을 방지한다.

사용자 환불에는 새 Outbox/스케줄러를 추가하지 않는다. 기존 카드 승인 통보 Outbox는 유지한다. 최초 사용자 환불은 승인 통보가 DELIVERED인 PG 거래에만 허용해, 통보 미완료 상태의 환불과 승인 거절 보상 경합을 방지한다.

## API와 호환성

- Ticksy 신청/조회: POST/GET `/api/v1/reservation/{reservationId}/refunds`
- Ticksy 재시도/철회: POST `/api/v1/reservation/{reservationId}/refunds/{processId}/retry` 및 `/withdraw`
- 가맹점 → PG: POST `/api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId}` 및 `/withdraw`
- PG → 카드사: POST `/internal/financial-simulations/cards/gateway/refunds/execute` 및 `/withdraw`

Ticksy는 사용자 JWT·예매 소유권, PG는 Merchant·credentialKey·가맹점 소유 원거래를 검증한다. 본문 결제수단으로 도메인을 선택하지 않는다. PG 컨트롤러는 GatewayPaymentRefundController로, Ticksy Feign은 PaymentGatewayRefundApplicationClient로 공통화했다. 기존 은행 URL·응답 필드·계좌 해시는 유지한다.

카드의 Ticksy 신청 예:

```json
{"eventId": 1, "selectedTicketIdList": [2, 3], "refundAccount": null}
```

가맹점이 보내는 PG 카드 요청 예:

```json
{"merchantId": 1, "buyerReference": "buyer", "amount": 20000}
```

카드 요청의 환불 계좌는 거절한다. 은행은 기존처럼 은행·계좌·예금주가 필요하며 최초 암호화 스냅샷으로 재전송한다. 카드 스냅샷에는 계좌 암호문을 저장하지 않는다. 공통 응답 필드는 pgPaymentId, refundRequestId, amount, outcome, completedAt, requestHash다. PG 미연결 기존 카드·가상계좌와 입금 전 은행 취소 경로는 유지한다.

## 상태와 멱등성

- PG 키는 원거래 PG ID + 환불 신청 ID다. 가맹점이 다르면 같은 paymentNo·환불 키도 충돌하지 않는다. 다른 가맹점의 거래는 조회·환불·철회할 수 없다.
- 카드 요청 해시는 CARD_REFUND_V1 + PG ID + 신청 ID + 정규화 금액이다. 같은 키의 다른 금액을 거절한다. 재시도마다 키를 생성하지 않는다.
- 전액·부분 취소/환불은 같은 API를 사용한다. 금융 원금 전액이면 REFUNDED, 남으면 PARTIALLY_REFUNDED다. 승인 거절 보상의 CANCELLED와 구분한다.
- 전체 티켓 취소와 금융 전액 환불은 별개다. 취소 수수료가 있으면 전체 취소여도 원금 전액이 반환되지 않는다. 기존 수수료·쿠폰·배송료 계산을 유지한다.
- 완료된 같은 대상의 요청도 최초 신청을 반환한다. 미완료 신청과 대상이 다르면 최초 신청 정보를 포함한 409다. 자동 대체하지 않는다. PG도 원거래마다 미완료 환불을 하나만 허용한다.
- 금융 철회가 먼저면 영구 WITHDRAWN 기록으로 지연 실행을 차단한다. 금융 환불이 먼저면 철회에도 최초 SUCCEEDED를 반환해 PG·Ticksy를 복구한다. 이미 이동한 자금을 되돌리는 기능은 아니다.
- 화면은 카드에도 좌석 선택·신청 이력·재시도·신청 취소를 제공한다. 완료·철회된 신청에는 재시도/신청 취소 버튼을 표시하지 않는다.

## 재시도와 부분 실패

Ticksy → PG는 자동 재시도하지 않는다. PG → 카드사는 같은 신청으로 최초 1회 + 추가 3회까지 시도한다. 연결·읽기 오류, 5xx, 해석 불가/불일치 응답은 재시도하고 확정 4xx는 즉시 거절한다. 4회 실패하면 503 결과 미확인으로 안내하고 브라우저에서 같은 신청으로 다시 요청한다.

카드 성공 후 PG 확정 실패는 카드 환불을 되돌리지 않는다. PG의 REQUESTED를 유지하고 같은 요청에서 카드의 최초 결과로 확정한다. PG 성공 후 Ticksy 응답 유실·로컬 저장 실패도 같은 PG 키로 복구한다. 사용자 요청 중단 후 자동 환불 재시도는 없으며 신청 이력에서 재시도·철회 확인이 필요하다.

Ticksy가 PG 성공을 확인한 뒤 로컬 반영에 실패해 LOCAL_FAILED를 보존한 경우, 같은 신청의 재시도는 금융 요청 없이 로컬 반영만 완료한다. PG 성공이 확인되지 않은 경우에만 최초 키로 PG를 다시 호출한다.

## 스키마와 적용

- gateway_card_refunds: PG의 최초 조건과 REQUESTED/SUCCEEDED/WITHDRAWN 결과.
- card_payment_refunds: 카드사의 최초 SUCCEEDED/WITHDRAWN 결과와 확정 시각.
- 두 테이블은 UNIQUE(pg_payment_id, refund_request_id)이며 각각 gateway_payments와 dummy_card_payment_histories를 참조한다. 매핑 전용 테이블은 추가하지 않는다.
- 최종 schema.sql과 기존 DB용 create-card-refund-tables.sql에 같은 정의를 반영했다. 새 테이블은 빈 상태로 추가하며 과거 누적 환불에 임의의 신청 키를 합성하지 않는다. 기존 카드·은행 환불 데이터는 변경하지 않는다.
- 로컬 PostgreSQL에는 두 테이블만 추가했다. 기존 테이블 삭제·초기화는 하지 않았다. Ticksy는 기존 프로세스·스냅샷 컬럼을 사용한다.

## 남은 범위

신규 PG 카드의 승인 전 결제수단 전환은 [카드→무통장 전환 계약](payment-gateway-card-method-change.md)으로 연결했다. 운영용 자동 불확실 환불 조정은 후속 범위다. 외부 카드 네트워크와 정산일별 실제 금전 이동은 모의 카드 원장으로 표현한다.

## 검증 결과

- 공통 모듈 45개, PG 전체 378개, Ticksy 결제·checkout·환불 회귀 298개: 총 721개 통과, 실패·건너뜀 0개.
- 새 카드 환불과 Ticksy의 카드·무통장 NOWAIT 경합은 별도 PostgreSQL 컨테이너에서 검증했다. 실제 로컬 DB에서는 스키마와 신규 카드 환불 기록 매핑을 검증하고 테스트 데이터를 모두 롤백했다.
- 금액 40.000/40.00의 동일 키 재요청과 40.001 거절을 추가 확인했다. 최종 카드·은행 환불 통합 테스트 26개가 통과했다.
- 프런트엔드 테스트 38개와 TypeScript/Vite 빌드가 통과했다. 카드의 계좌 없는 신청·미완료 신청 버튼과 기존 은행 화면을 검증했다.
- 최종 PG·Ticksy bootJar 빌드가 통과했다.
- PG·Ticksy·브라우저 전체를 실행한 수동 결제/환불은 이번 검증에 포함하지 않았다. 금융 HTTP는 실제 RestClient와 실행한 테스트 서버로 검증했다.
