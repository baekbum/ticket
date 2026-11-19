# PG 거래 기반 가상계좌 만료

> 2026-11-18: 구형 paymentNo 전용 PG/금융 API와 X-Service-Token 전용 PG 인증은 폐쇄했다. 아래의 구형 경로 설명은 이전 구조 기록이며 호출 계약으로 사용하지 않는다. 현재 변경 범위와 보존한 API는 [레거시 정리 기록](payment-legacy-retirement.md)을 따른다. 은행 입금은 `/api/v1/banks/virtual-accounts/deposit`, 환불은 신규 PG 거래·가맹점 범위 API를 사용한다.

2026-11-12 신규 PG 연결 계좌는 은행→PG 만료 Outbox와 Ticksy의 PG 상태 조회를 사용한다. 만료 시각은 Ticksy가 조회 후보를 고르는 기준이며, 실제 예매·좌석 만료는 PG가 은행 만료를 확정한 EXPIRED 상태로만 처리한다.

## 상태의 근거

은행에 기한 내 입금이 커밋됐다면 PG·Ticksy 통보가 늦어져도 입금된 거래다. Ticksy의 현재 시각이 입금기한을 넘었다는 사실만으로 좌석을 반납하지 않는다. PG가 PAID여도 입금자·실제 입금 시각 등의 확정 정보는 기존 입금 Outbox 통보로 반영하며, 그 전에는 좌석을 유지한다.

~~~mermaid
sequenceDiagram
    participant B as 은행
    participant PG as PG
    participant T as Ticksy 스케줄러
    B->>B: 계좌 잠금 → 미입금·기한 확인
    B->>B: EXPIRED + 은행 이력 + BANK_EXPIRATION Outbox 커밋
    B->>PG: 금융 인증 HTTP 만료 이벤트
    PG->>PG: 원거래·계좌·기한 검증 → PG 상태·EXPIRE 이력 커밋
    PG-->>B: 최초 이벤트 수신 확인
    B->>B: 해당 Outbox DELIVERED
    T->>PG: 가맹점 인증 GET 거래 상태
    PG-->>T: EXPIRED / PAID / WAITING_DEPOSIT 등
    opt PG가 EXPIRED를 확정한 경우
        T->>T: 예매·티켓·Payment 만료 + 좌석·구매 매수·쿠폰 정리
    end
~~~

PG→Ticksy 만료 통보 Outbox는 만들지 않는다. Ticksy→PG는 읽기 전용 상태 조회이므로 Outbox가 필요 없다. 입금·취소의 기존 통보 Outbox는 유지한다.

## 은행 만료와 경합

BankVirtualAccountExpirationScheduler는 기본 30초 간격으로 PG 연결 WAITING_DEPOSIT 계좌 중 기한이 지난 ID를 조회한다. ID 커서로 페이지를 순회하여 한 계좌의 실패가 뒤의 계좌 처리를 막지 않는다.

BankVirtualAccountExpirationService는 건별 로컬 트랜잭션에서 입금과 동일한 계좌 행을 PESSIMISTIC_WRITE로 잠그고 refresh한 뒤 상태를 다시 확인한다.

- DEPOSITED이거나 depositedAt이 존재하면 만료하지 않는다.
- CANCELLED·EXPIRED 등 입금 대기 이외 상태는 다시 만료하지 않는다.
- 은행 만료 시각이 expiresAt을 엄격히 지난 경우에만 확정한다. 정확한 기한 시각의 입금은 기존 정책대로 허용한다.
- 입금 트랜잭션이 먼저 계좌를 잠갔다면 만료는 커밋·롤백을 기다린다. 입금 커밋 후에는 만료를 건너뛰고, 입금 롤백 후 미입금이면 만료한다.
- 계좌 상태·은행 만료 이력·Outbox 저장은 함께 커밋하거나 함께 롤백한다.

## 은행→PG 계약과 멱등성

기존 bank_transfer_outbox에 BANK_EXPIRATION flow를 추가했다. 이벤트 식별자는 pgPaymentId + :EXPIRE:INITIAL로 고정한다. payload는 PG ID·계좌번호·은행·금액·최초 기한·은행 만료 확정 시각이며 비밀값과 통보 URL은 담지 않는다.

POST /internal/financial-simulations/gateway/bank-expirations는 기존 금융 전용 인증으로 보호한다. 일반 가맹점 토큰·브라우저 JWT로는 호출할 수 없다.

GatewayVirtualAccountExpirationCommands는 PG 거래를 잠그고 수단·은행·금액·기한·최초 발급 계좌를 검증한다. PAID·부분 환불·환불처럼 금융 입금 사실이 있는 거래를 만료로 덮어쓰지 않는다. 이미 확정 취소된 거래도 CANCELLED를 유지한다. PG 상태와 EXPIRE/INITIAL 이력은 같은 트랜잭션에 저장한다.

같은 이벤트는 최초 내용이 모두 일치할 때만 성공으로 재응답한다. 같은 키에 계좌·금액·기한·만료 확정 시각이 달라지면 거절한다. 성공 응답은 PG 로컬 커밋 후 BankExpirationAcknowledgement로 최초 이벤트 전체를 확인한다. 은행은 정확한 HTTP 200 수신 확인만 DELIVERED로 인정한다.

은행 Outbox 발행기는 기존 60초 임대·시도 번호·30초 실패 재시도를 재사용한다. HTTP 동안 은행·PG·Outbox DB 잠금을 유지하지 않는다. PG 커밋 후 응답이 유실되면 같은 이벤트로 재전송하며 PG 이력을 중복 생성하지 않는다.

## Ticksy의 조회와 로컬 만료

VirtualAccountExpirationScheduler는 기본 60초마다 최초 gatewayExpiresAt이 지난 신규 BANK_TRANSFER의 READY·WAITING_DEPOSIT 결제를 조회한다. ID 커서로 순회하여 한 거래의 조회 실패가 다른 거래를 막지 않는다.

PG ID가 있으면 GET /api/v1/payments/gateway/{pgPaymentId}, 준비 응답 저장 전 중단돼 PG ID가 없으면 GET /api/v1/payments/gateway/by-payment-no/{paymentNo}를 사용한다. PG가 인증된 merchant 범위로 조회하며 Ticksy도 서버 merchant ID·결제번호·예매자·수단·은행·금액·최초 기한·PG ID를 검증한다. 같은 결제번호를 쓰는 다른 가맹점의 결과는 수락하지 않는다.

| PG 응답 | Ticksy 동작 |
|---|---|
| EXPIRED | 원거래 확인 후 기존 원자적 만료 처리를 실행 |
| PAID·환불 상태 | 만료하지 않고 기존 입금 결과 통보와 정산 상태를 유지 |
| READY·WAITING_DEPOSIT 등 | 현재 예매·좌석 유지, 다음 주기에 확인 |
| 404·통신 실패·빈/불일치 응답 | 만료 보류, 다음 주기에 확인 |

HTTP 조회는 DB 트랜잭션 밖에서 수행한다. 최종 반영은 예약 → Payment 순서로 잠그고 최신 상태를 읽는다. 그 사이 PAID·CANCELLED·EXPIRED가 됐다면 늦은 조회 결과로 다시 만료하지 않는다.

확정 EXPIRED이면 PaymentExpirationService.expire를 재사용해 Payment·Reservation·Ticket 만료, 좌석 AVAILABLE, 구매 매수 반환, 배송 취소, 쿠폰 복원, 좌석 캐시 동기화를 함께 처리한다. 로컬 반영 실패 시 모두 롤백하며 다음 조회로 재시도한다. 이미 만료된 결제는 다시 처리하지 않는다.

기존 시간 기준 PaymentExpirationScheduler는 신규 PG 무통장을 READY 상태부터 제외한다. 2026-11-18 구형 Kafka 만료 리스너·생산자와 Ticksy 직접 만료 진입점을 제거했다. 신규 계좌는 PG의 확정 상태 조회로만 만료하며, 기존 Kafka 브로커 토픽 자체는 삭제하지 않았다.

## 스키마와 배포

새 테이블과 컬럼은 추가하지 않는다. 두 schema.sql에 다음 변경을 반영한다.

- bank_transfer_outbox의 chk_bank_transfer_flow에 BANK_EXPIRATION 허용.
- dummy_virtual_accounts(status, expires_at, pg_payment_id) 조회 인덱스.
- payments(method, status, gateway_expires_at, payment_id) 조회 인덱스.

기존 DB에는 제약 확장과 인덱스 생성만 적용한다. 결제·계좌 테이블 삭제·초기화와 자금 반환은 수행하지 않는다. 별도 add-*.sql 파일은 만들지 않는다.

## 남은 범위

PG 미등록·발급 전 READY 거래는 EXPIRED가 확정되지 않았으므로 시간만으로 자동 만료하지 않는다. PG 금융 사실 확인이나 발급 복구·명시적 취소·관리자 확인이 필요하다. 특히 발급 전에 요청이 중단된 거래의 PG 만료 정책은 별도 범위이며, 조회 404만으로 안전하게 좌석을 반환했다고 가정하지 않는다.

신규 PG 환불·카드의 실제 호출 전환과 관리자 수동 정산 전환은 계속 후속 작업이다. 늦은 입금의 실제 자금 반환 기능도 추가하지 않는다.

## 검증

실제 은행→PG HTTP와 Ticksy→PG HTTP 조회를 포함해 만료·멱등 재전달·응답 유실·가맹점 격리·은행 Outbox 실패·PG 이력 실패·로컬 정리 실패의 롤백을 검증한다. 기한 내 입금의 지연 통보, 입금과 만료 잠금 경합, 만료 조회 중 입금·취소 완료, 조회 실패 뒤 다른 결제와 다음 페이지 처리도 확인한다.

최종 검증 결과: 공통 45개·PG 333개·Ticksy 256개로 총 634개 통과했다. PG 결과에는 실제 PostgreSQL에서 DDL을 검증하고 데이터 변경을 롤백한 24개 테스트가 포함된다. 별도 테스트 DB가 필요한 checkout 11개는 제외했다. 실제 DB와 두 schema.sql의 44개 대상 테이블·492개 컬럼·120개 제약·63개 명시적 인덱스를 대조했으며 불일치는 없었다.
