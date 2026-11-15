# PG 연결 가상계좌 환불·신청 철회

2026-11-14 컨트롤러는 업무별 패키지로 분류했다. 신규 GatewayBankRefundController와 기존 resolve의 GatewayRefundController는 controller.gateway.refund에 둔다. 패키지 분류 단계이며 카드 환불까지 공통 구현으로 전환한 것은 아니다. API 경로와 인증 계약은 유지한다.

2026-11-13 구현. 신규 PG ID가 연결된 Ticksy 무통장 결제의 입금 후 전액·부분 환불을 처리한다. 기존 카드·PG 미연결 계좌의 환불과 입금 전 취소 Outbox는 유지한다.

## 호출과 재시도

~~~mermaid
sequenceDiagram
    participant U as 브라우저
    participant T as Ticksy
    participant P as PG
    participant B as 은행
    U->>T: 선택 티켓·환불 계좌로 신청
    T->>T: 예매 NOWAIT 잠금 → 최초 스냅샷·신청 ID 커밋
    T->>T: 예매 NOWAIT 재잠금 → 최신 신청 확인
    T->>P: 같은 신청 ID로 환불 HTTP 1회
    P->>P: 원거래·가맹점·요청 해시 검증 → REQUESTED 커밋
    P->>B: 동일 키·동일 내용으로 최대 4회
    B->>B: 계좌 잠금 → 누적 환불액·이력·금융 결과 함께 커밋
    B-->>P: 최초 SUCCEEDED 또는 WITHDRAWN 결과
    P->>P: PG 환불액·상태·이력·결과 함께 커밋
    P-->>T: 확정 결과
    T->>T: 성공이면 Payment·티켓·예매·좌석·이력 함께 커밋
    T-->>U: 환불 성공 / 신청 철회 / 결과 미확인
~~~

- Ticksy→PG Feign은 NEVER_RETRY이며 사용자 요청당 한 번만 호출한다. 브라우저도 POST를 자동 반복하지 않는다.
- PG→은행은 연결·응답 유실, 5xx, 응답 해석 실패, 불일치 응답에 최초 1회 + 추가 3회까지 같은 요청을 전송한다. 확정 4xx는 재시도하지 않는다.
- 네 번 모두 확인하지 못하면 Ticksy는 HTTP 503 GATEWAY_REFUND_UNCONFIRMED를 반환한다. 이것은 금융 실패 확정이 아니다. 신청은 남고 사용자는 같은 신청으로 다시 시도한다.
- 은행 HTTP는 연결 2초·읽기 15초, Ticksy 환불 Feign은 연결 2초·읽기 80초다. 실행 트랜잭션은 95초, Nginx /ticket/ 응답 대기는 100초다. 상위 프록시도 이 대기 시간을 허용해야 한다.
- 환불 전용 Outbox나 백그라운드 실행 스케줄러를 추가하지 않는다. 입금·만료·입금 전 취소의 기존 Outbox는 별도다.

## 신청 스냅샷과 멱등성

PaymentRefundProcess → PaymentRefund → PaymentRefundSnapshot → PaymentRefundSnapshotTicket 구조를 재사용한다. 최초 대상 티켓·가격·환불액·취소 수수료·환불 계좌·계산 시각을 저장하고 이후 금액을 다시 계산하지 않는다. 티켓 집합의 순서는 동일성에 영향을 주지 않는다.

Ticksy의 paymentRefundProcessId 문자열이 refundRequestId다. PG·은행의 UNIQUE(pg_payment_id, refund_request_id)가 가맹점 간 충돌을 방지한다. paymentNo만으로 환불을 식별하지 않는다. 같은 키에 금액·은행·계좌·예금주가 바뀌면 거절한다. 요청 해시는 원거래·환불 키까지 포함한다.

예매마다 하나의 미완료 신청만 허용한다. 내용이 다른 신청은 409 REFUND_REQUEST_CONFLICT와 기존 신청의 마스킹된 정보를 반환한다. 기존 요청을 자동 종료하거나 교체하지 않는다. 완료된 동일 신청은 취소된 티켓을 다시 검사하기 전에 최초 성공을 반환한다. 완료 응답이 유실돼도 새 신청을 만들지 않는다.

사용자 소유권은 인증된 사용자와 Reservation으로, PG 소유권은 인증된 Merchant와 저장된 gateway_payments로 확인한다. 본문 userId·merchantId만으로 권한을 부여하지 않는다. PG가 다른 가맹점의 거래를 조회·환불·철회할 수 없다.

## 환불 신청 취소

여기서 취소는 이미 지급한 환불을 되돌리는 작업이 아니라 미실행 신청의 철회다.

1. Ticksy가 WITHDRAW_REQUESTED를 먼저 커밋한다. 응답을 잃어도 그 신청으로 환불 실행을 다시 시작하지 않는다.
2. PG가 최초 스냅샷과 동일한 키·금액·계좌로 은행 철회를 요청한다.
3. 은행은 환불과 같은 계좌를 잠근다. 먼저 성공한 환불이 있으면 최초 SUCCEEDED 결과를 반환한다. Ticksy는 좌석·티켓·Payment 환불 완료를 복구하고 이미 완료된 신청이라고 안내한다.
4. 환불이 실행되지 않았다면 은행에 WITHDRAWN 기록을 남긴다. 이후 지연된 원래 환불 요청도 WITHDRAWN을 받아 금융 실행을 하지 않는다.
5. PG·Ticksy까지 WITHDRAWN이 확인되면 신청만 종료한다. 예매·티켓·좌석은 그대로이며 다른 좌석으로 새 신청 ID를 발급할 수 있다.
6. 철회 응답이 확인되지 않으면 WITHDRAW_REQUESTED를 유지하고 새 신청을 차단한다. 사용자는 철회 확인을 재시도한다.

브라우저의 다시 시도·환불 신청 취소 버튼은 LOCAL_SUCCEEDED·WITHDRAWN·SUPERSEDED가 아닌 신청에만 표시한다. 처리 중에는 비활성화한다. WITHDRAW_REQUESTED의 다시 확인은 환불 실행이 아니라 철회 API를 호출한다.

## 잠금과 트랜잭션

사용자가 선택한 정책에 따라 Ticksy 실행 트랜잭션은 Reservation의 FOR UPDATE NOWAIT 잠금을 PG HTTP부터 로컬 결과 커밋까지 유지한다. 같은 예매에 재시도·철회가 겹치면 기다리지 않고 409 RESERVATION_PROCESSING과 다음 안내를 반환한다.

> 해당 예매에 대한 처리가 진행 중입니다. 잠시 후 다시 시도해 주세요.

최초 신청 저장과 실행은 별도 트랜잭션이다. 그 사이 다른 작업이 완료되거나 철회할 수 있으므로 실행 시 예매를 다시 잠그고 신청 상태를 재검사한다. PG는 은행 HTTP 동안 PG 거래 잠금을 유지하지 않는다. 은행은 자체 계좌 잠금으로 환불/철회를 직렬화한다.

은행의 누적 환불액·은행 이력·금융 결과는 한 트랜잭션이고, PG의 상태·금액·이력·결과는 별도 한 트랜잭션이다. Ticksy의 로컬 예매 정리도 별도 트랜잭션이다. 같은 물리 DB여도 HTTP를 넘는 공동 롤백을 가정하지 않는다. 은행만 성공한 경우 같은 영수증으로 PG를 복구한다. Ticksy 반영 실패도 최초 신청과 PG 성공을 보존하여 같은 신청으로 복구한다.

은행의 DEPOSITED 상태·최초 입금 금액·시각은 환불로 지우지 않는다. 별도 누적 환불액과 이력으로 반환 금액을 구분한다. 입금 전 취소·늦은 입금의 실제 반환 정책은 변경하지 않는다.

## API와 호환성

아래 경로는 서비스 내부 경로다. 브라우저의 Ticksy URL에는 /ticket, 외부 PG URL에는 /payment-gateway 프록시 접두사가 붙는다.

| 호출 | API | 인증·내용 |
|---|---|---|
| 브라우저→Ticksy | POST /api/v1/reservation/{reservationId}/refunds | 사용자 JWT, eventId·selectedTicketIdList·refundAccount |
| 브라우저→Ticksy | GET /api/v1/reservation/{reservationId}/refunds | 사용자 JWT, 소유 예매의 신청 목록 |
| 브라우저→Ticksy | POST /api/v1/reservation/{reservationId}/refunds/{processId}/retry | 사용자 JWT, 본문 없이 최초 스냅샷 재사용 |
| 브라우저→Ticksy | POST /api/v1/reservation/{reservationId}/refunds/{processId}/withdraw | 사용자 JWT, 본문 없이 최초 스냅샷 재사용 |
| Ticksy→PG | POST /api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId} | Merchant·credentialKey, buyerReference·amount·환불 계좌 |
| Ticksy→PG | POST 위 경로/withdraw | 같은 인증·같은 최초 내용 |
| PG→은행 | POST /internal/financial-simulations/banks/virtual-accounts/gateway/refunds/execute | 기존 금융 전용 토큰, BankRefundCommand |
| PG→은행 | POST 위 금융 경로의 /withdraw | 기존 금융 전용 토큰, 동일 BankRefundCommand |

Ticksy 성공 응답은 outcome(SUCCEEDED 또는 WITHDRAWN), application, message다. application은 기존 PaymentRefundProcessResponse에 cancellationFeeAmount를 추가한 계약이며 계좌 원문·암호문은 노출하지 않는다. PG·은행 응답은 원거래·환불 키·최초 금액·결과·최초 완료 시각·요청 해시를 포함한다. Ticksy와 PG가 원요청과 대조한다.

기존 PUT /api/v1/reservation/cancel/id/{reservationId}는 입금 전 취소와 기존 카드·구계좌용으로 유지한다. 새 PG 연결 계좌의 입금 후 환불은 새 POST API를 사용해야 한다. 기존 PUT에 대한 신규 PG 환불 차단을 제거하지 않는다. MyTicketDetailDialog는 pgPaymentId가 있는 무통장 결제만 새 API로 분기한다. 카드 승인·환불과 기존 관리자 환불 resolve 계약은 그대로다.

## 스키마와 설정

최종 DDL은 PG·Ticksy의 src/main/resources/sql/schema.sql이다. 기존 실행 중 local-postgres/ticket DB에 다음 변경만 추가 적용했으며 테이블 삭제·초기화는 하지 않았다.

| 대상 | 변경 |
|---|---|
| gateway_bank_refunds | 새 테이블. PG 원거래 FK, 요청 키·금액·해시·REQUESTED/SUCCEEDED/WITHDRAWN·최초 완료 시각 |
| bank_virtual_account_refunds | 새 테이블. 은행 계좌 PG ID FK, 동일 키·금액·해시·금융 확정 SUCCEEDED/WITHDRAWN 기록 |
| dummy_virtual_accounts | refunded_amount NUMERIC(15,2) NOT NULL DEFAULT 0, 0 이상 원금 이하 CHECK |
| payment_refund_snapshots | nullable refund_account_ciphertext TEXT |

기존 기록을 임의로 새 merchant·PG 거래에 매핑하지 않는다. 기존 계좌·환불은 종전 경로를 유지한다. 과거 금융 이력이 있는 계좌를 신규 경로로 이관하려면 가맹점·PG 원거래·누적 환불액을 대사해야 한다. 새 카운터 기본값 0이 과거 환불을 대사했다는 뜻은 아니다. 가맹점을 확인할 수 없는 데이터는 자동 이관하지 않는다.

계좌 원문은 Ticksy 스냅샷에 AES-GCM v1 암호문으로 저장한다. APP_REFUND_ACCOUNT_ENCRYPTION_KEY 또는 app.payment-refund.account-encryption-key를 외부에서 주입한다. 키 미설정이면 신규 환불 접수만 실패하며 기존 업무의 시작을 막지 않는다. 로컬에서는 git 제외된 ticket-service/pg-merchant.properties에 생성된 키가 있다. 소스·DDL에 비밀값을 넣지 않는다. 키는 재시작 후에도 유지해야 하며 교체 시 기존 미완료 암호문의 재암호화/키 버전 운영이 필요하다. 현재 자동 키 교체 기능은 없다.

## 검증 범위와 남은 제한

- PG 통합 테스트: 부분·잔액 전액 환불, 동일 키 변경 거절, 가맹점 간 동일 번호/키, 다른 가맹점 차단, 은행 환불/철회 경합, 3회·4회 응답 유실, 응답 해석 실패, 은행·PG 저장 실패의 로컬 롤백과 재요청 복구.
- Ticksy 실제 PostgreSQL 통합 테스트: 최초 신청 커밋, 완료 응답 재생, 대상·계좌 변경 차단, 미확인 결과 수동 재시도, 철회·이미 성공한 환불 복구, 소유권, 로컬 실패 롤백, HTTP 중 NOWAIT 즉시 충돌.
- 컨트롤러 409/503 계약과 브라우저의 미완료 버튼·자동 재요청 금지·완료 복구를 검증한다.
- 신규 카드의 Ticksy PG ID 기반 환불 전환, 기존 관리자 paymentNo 대사 경로 전환, 과거 데이터 자동 이관은 이번 범위에 포함하지 않는다.
- 은행은 프로젝트 내부 모의 금융 원장이다. 실은행 지급망·실명 조회·실제 송금 기능을 추가하지 않는다.

2026-11-13 실행 결과: 공통 45개·PG 353개·Ticksy 276개로 백엔드 674개, 프런트엔드 32개 모두 통과했고 제외된 테스트는 없다. 실제 PostgreSQL checkout 재시도 14개와 신규 환불 NOWAIT 테스트도 실행했다. 두 schema.sql의 46개 테이블·508개 컬럼·130개 제약·63개 명시적 인덱스가 실행 중 DB와 일치한다.
