# Ticksy의 PG 거래 기반 가상계좌 호출

2026-11-11부터 새 무통장 checkout은 기존 paymentNo 단독 발급 대신 가맹점 인증 기반 PG prepare·발급·비동기 취소 API를 호출한다. 기존 계좌는 기존 계약을 유지한다. 카드 프런트엔드의 기존 JWT 승인 호출은 이번 변경 대상에 포함하지 않는다.

## 실제 요청 경로

~~~mermaid
sequenceDiagram
    participant U as 사용자
    participant T as Ticksy
    participant PG as PG
    participant B as BankController
    U->>T: checkout/prepare → checkout/confirm
    T->>T: 예매·Payment·불변 발급 조건 커밋
    T->>PG: POST /payments/gateway/prepare
    PG-->>T: pgPaymentId + 최초 준비 조건
    T->>T: 원거래 검증 후 pgPaymentId 커밋
    T->>PG: POST /virtual-account/gateway/{pgPaymentId}/issue
    PG->>B: 기존 계좌 조회 → 필요 시 발급
    PG-->>T: 최초 계좌·금액·기한
    T->>T: 계좌·WAITING_DEPOSIT 반영 커밋
    T-->>U: 기존 PaymentResponse + pgPaymentId
    B->>PG: 입금 Outbox
    PG->>T: 가맹점 입금 Outbox
    T-->>PG: APPLIED 또는 RESERVATION_CANCELLED
~~~

위 PG URL의 실제 접두사는 /api/v1/payments다. 브라우저는 PG 서비스 토큰을 받지 않으며 기존 Ticksy checkout URL과 사용자 인증·대기열 검증을 계속 사용한다. PaymentResponse에 nullable pgPaymentId만 추가했다. 프런트는 기존 checkout 멱등키를 재사용하며, 재시도 응답이 이미 PAID라면 입금 대기 안내 대신 완료 화면을 표시한다.

## 책임과 트랜잭션

- CheckoutService는 NEVER 오케스트레이터다. CheckoutTransactionService가 예매·티켓·Payment·발급 의도를 한 로컬 트랜잭션으로 저장한다. 카드→무통장 전환의 기존 카드 승인 이력 조회도 잠금 밖에서 실행하고, 로컬 단계에서 소유자·상태·선점 기한을 다시 확인한다.
- CheckoutVirtualAccountService는 은행 코드와 공연일 규칙으로 발급 조건만 저장한다. 공연 당일은 거절, 전날은 당일 23:59:59, 나머지는 다음날 23:59:59다. 계산한 기한을 저장하고 재시도 시 현재 날짜로 다시 계산하지 않는다.
- GatewayVirtualAccountCheckoutService는 DB 트랜잭션 밖에서 PaymentGatewayTransactionClient의 Feign HTTP를 호출한다. 각 준비·발급 결과 반영은 GatewayVirtualAccountCheckoutCommands의 짧은 REQUIRED 트랜잭션이다. 예약 → Payment 순서로 잠근다.
- prepare 응답의 가맹점·결제번호·사용자·수단·금액·은행·기한을 검증한 뒤 PG ID를 저장한다. 발급 응답에서도 원거래·계좌·기한·최초 은행 상태를 검증한다.
- 외부 HTTP가 성공한 뒤 Ticksy 저장이 실패하면 PG 원거래를 롤백했다고 주장하지 않는다. 저장된 결제번호·조건과 PG의 멱등 응답으로 재개한다.

신규 Feign 클라이언트는 기존 PaymentGatewayFeignConfig의 X-Service-Token을 재사용한다. PG는 이 자격증명에 연결된 merchant를 결정하며, 본문의 merchantId는 Ticksy 서버 설정 app.payment-gateway.merchant-id와 교차 검증하는 값이다. 일반 사용자 입력으로 가맹점이나 통보 주소를 선택하지 않는다.

## 요청 안에서 추가 3회 재시도와 사용자 재요청

2026-11-12부터 별도 발급 복구 디스패쳐를 제거했다. gateway_issue_pending은 PG 발급 결과가 Ticksy에 아직 반영되지 않았다는 표시로만 사용한다. gateway_next_attempt_at·복구 조회 인덱스·예약 재시도 쿼리도 제거했다. 일반 발급의 백그라운드 자동 재시도는 수행하지 않는다.

- GatewayVirtualAccountCheckoutService.issue는 같은 브라우저 HTTP 요청 안에서 최초 1회 + 추가 3회, 최대 4회 시도한다. 네 번째 시도에서 성공해도 해당 요청에 정상 PaymentResponse를 반환한다.
- 타임아웃·연결 실패, PG 5xx, Spring의 일시적인 DB 오류만 재시도한다. 인증·금액·은행·원거래 계약 불일치, PG 4xx, 영구 DB 오류는 즉시 반환한다. 작업 스레드가 중단되면 추가 시도를 멈추고 중단 표시를 유지한다.
- 매 시도마다 커밋된 최초 요청과 PG ID를 다시 읽는다. prepare 응답 유실은 같은 paymentNo·은행·금액·사용자·입금기한으로 재요청한다. PG ID가 저장돼 있으면 prepare를 생략하고 기존 PG ID로 issue한다.
- Feign 자체 Retryer는 NEVER_RETRY로 고정해 내부 재시도로 호출 횟수가 증가하지 않게 한다. 신규 거래 클라이언트에만 연결 1초·읽기 3초 타임아웃을 설정한다. DB 처리 등 checkout 전체 대기 시간은 별도로 고려해야 한다.
- 네 번 모두 일시적인 오류로 끝나면 HTTP 503과 GATEWAY_ACCOUNT_ISSUE_UNCONFIRMED를 반환한다. 프런트는 서버 원문 대신 고정 안내문으로 같은 결제 요청 재시도·예매 내역 확인을 안내한다. 기존 checkout 멱등키는 초기화하지 않는다.
- PG에는 이미 계좌가 발급됐을 수 있으므로 한도 소진만으로 Payment·예매·PG 거래를 취소하거나 새로 생성하지 않는다. 사용자가 같은 checkout 멱등키로 재요청하면 저장한 최초 조건과 PG ID로 기존 결과를 복구한다. 프로세스가 중단된 경우에도 사용자 재요청으로 재개하며 자동 발급 복구는 하지 않는다.
- 발급 결과를 정상 반영하면 pending을 false로 변경한다. 이후 재요청은 추가 HTTP 없이 로컬 결과를 반환한다. 같은 결제번호에 다른 은행을 보내면 거절한다.
- 발급 응답보다 입금 통보가 먼저 도착하면 저장한 PG ID와 최초 조건을 검증하여 계좌를 연결하고 입금을 반영한다. 늦은 발급 응답은 PAID를 WAITING_DEPOSIT으로 되돌리지 않는다.

~~~text
브라우저 checkout 요청
→ Ticksy 로컬 예매·Payment·최초 조건 커밋
→ PG 요청 최초 1회 + 추가 3회
→ 성공하면 같은 HTTP 요청에 정상 응답
→ 한도 소진이면 결과 미확인 503 응답
→ 사용자가 같은 checkout 멱등키로 다시 요청
~~~

## 입금 전 취소

1. Ticksy는 예매·티켓·Payment 취소, 좌석 AVAILABLE 복원, 구매 매수 조정, virtual_account_cancel_outbox 저장을 함께 커밋한다. 발급 전 READY인 신규 무통장 결제도 전체 취소할 수 있다.
2. 발급 중 취소되면 Outbox에 원래 은행·금액·소유자를 먼저 남긴다. 계좌·PG ID가 아직 null이면 취소 발행기가 해당 원거래에 한해 제한 재시도로 발급 결과를 확인·연결한 뒤 같은 취소 작업을 보낸다. 이것은 명시적인 예매 취소의 후속 정리이며 일반 발급을 자동 완료하는 스케줄러가 아니다. 늦은 발급 응답으로 예매나 좌석을 되살리지 않는다.
3. 발행기는 60초 임대와 시도 번호로 짧게 선점한다. HTTP 동안 DB 잠금을 유지하지 않는다. 프로세스 중단 시 임대가 만료되면 같은 작업을 다시 선점하고, 이전 작업자의 늦은 결과는 무시한다.
4. 신규 거래는 POST /api/v1/payments/virtual-account/gateway/{pgPaymentId}/cancel을 사용한다. 202 PROCESSING이면 요청 전달 상태를 DELIVERED로 기록한다. 이 상태가 금융 취소 성공을 의미하지 않는다.
5. PG의 최종 BANK_CANCELLATION 통보는 기존 등록 통보 URL에서 수신한다. PG ID·가맹점·사용자·계좌·은행·금액·최초 요청을 검증하고 Outbox의 gateway_result/gateway_reason과 수신 receipt를 같은 트랜잭션에 저장한다. 최종 결과는 CANCELLED 또는 REJECTED다.
6. 최종 통보가 202 응답보다 먼저 도착하거나 수신 확인 응답이 유실돼도 같은 결과를 반환한다. 늦은 접수 응답·전송 실패는 최종 결과를 덮어쓰지 않는다.

취소된 예매의 입금 통보에는 RESERVATION_CANCELLED를 반환한다. 첫 입금 사실을 지우거나 좌석을 다시 확보하지 않는다. 실제 늦은 입금 자금 반환은 구현하지 않으며 fundsReturned=false다. 은행이 취소를 거절해도 Ticksy의 취소된 예매를 자동 복원하지 않는다.

## 스키마와 기존 데이터

최종 DDL은 ticket-service/src/main/resources/sql/schema.sql이다. 최초 연동 때 실행 중 PostgreSQL의 두 테이블에 컬럼·제약·인덱스와 계좌번호 NOT NULL 해제를 적용했다. 2026-11-12에는 발급 재시도 시각 컬럼과 전용 조회 인덱스만 제거했다. 테이블 삭제·초기화는 하지 않았다. 별도의 add-*.sql 파일은 추가하지 않았다.

| 테이블 | 변경 |
|---|---|
| payments | nullable pg_payment_id VARCHAR(44), PG ID UNIQUE, gateway_bank_company, gateway_expires_at, gateway_issue_pending DEFAULT FALSE |
| virtual_account_cancel_outbox | nullable pg_payment_id·buyer_reference·bank_company, nullable account_number, gateway_result·gateway_reason, delivery_attempt DEFAULT 0·lease_until |

기존 Payment는 PG ID·발급 은행 조건을 null, pending을 false로 유지한다. 이전 Outbox의 bank_company도 null이므로 기존 동기 취소 계약을 사용한다. 결제번호가 같다는 이유만으로 새 PG 거래를 자동 생성하거나 기존 계좌에 연결하지 않는다.

기존 데이터를 신규 흐름에 옮기려면 서버에 등록된 merchant와 PG 원거래를 확인하고 merchant_payment_no·금액·사용자·은행·계좌가 일치하는지 검증한 뒤 PG ID·고정 조건을 함께 설정해야 한다. 가맹점을 입증할 수 없는 데이터는 기존 경로에 격리하고 수동 확인한다. 이름만으로 가맹점을 추론하지 않는다. 비밀값은 데이터 이전 SQL에 넣지 않는다.

## 현재 제한과 다음 단계

신규 PG 원거래의 환불과 만료 통보 전환은 후속 작업이다. 입금 완료된 신규 거래의 예매 취소·환불과 기존 관리자 paymentNo 정산 API는 명시적으로 거절한다. 기존 계좌는 종전 환불·만료·관리자 정산을 계속 사용한다. 신규 발급 계좌의 자동 만료에서 Ticksy 좌석 정리까지의 연결은 아직 완료되지 않았으므로 전체 결제 서비스 배포 전에 반드시 연결해야 한다.

기한이 지난 미완료 발급이나 영구적인 인증·등록 URL·데이터 불일치는 제한 재시도나 같은 키 재요청만으로 해결되지 않는다. pending 정보와 로그를 보존하며 관리자 확인이 필요하다. 신규 결제수단을 운영에 활성화하기 전에 등록 merchant·통보 endpoint·암호화된 통보 자격증명이 준비돼 있어야 한다.

## 검증

GatewayVirtualAccountCheckoutIntegrationTest는 별도 H2 DB와 실제 HTTP 서버·Feign 호출로 준비/발급/취소 응답 유실, 최초 요청 재사용, 잘못된 금액·PG ID, 발급 중 취소, 먼저 도착한 입금 통보, 먼저 도착한 최종 취소 통보, 수신 receipt 실패·Outbox 실패의 로컬 롤백을 검증한다. HTTP 전송 시 로컬 트랜잭션이 없다는 것도 확인한다. 기존 checkout·입금·취소·환불·카드 회귀 테스트를 함께 실행한다.

이전 전체 연동 회귀 결과는 공통 45개·PG 295개·Ticksy 232개 통과였다. 2026-11-12 변경은 요청 내 재시도 횟수·한도 소진 후 같은 키 재요청·타임아웃·영구 오류 즉시 반환·취소 Outbox 정리 및 사용자 오류 안내를 추가 검증한다.

2026-11-12 검증 결과: 공통 45개·Ticksy 245개로 백엔드 290개 통과, 별도 PostgreSQL 환경이 필요한 checkout 테스트 11개 제외. 프런트 오류 안내·인증 재요청 테스트 12개와 프로덕션 빌드도 통과했다. 실제 DB와 두 schema.sql의 44개 대상 테이블·492개 컬럼·120개 제약·61개 명시적 인덱스를 읽기 전용으로 대조해 불일치가 없음을 확인했다.
