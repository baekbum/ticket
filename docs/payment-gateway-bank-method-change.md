# 미발급 무통장 거래의 카드 전환

2026-11-16. 브라우저가 계좌 발급 실패를 받은 뒤 동일 checkout 키로 CREDIT_CARD를 요청할 수 있다. 실패 응답만으로 미발급을 판단하지 않는다.

## 허용 정책

| 은행 사실 | 결과 |
|---|---|
| 미발급 확인 + 이후 발급 차단 확정 | 카드로 전환 |
| 발급 사실 존재 | 입금·취소·만료 여부와 무관하게 409 거절 |
| 입금 완료 | 거절 |
| 상태 확인 불가 또는 응답 유실 | 수단 유지, 같은 요청 재시도 |

최초 카드의 무통장 전환은 기존 승인 차단·승인 성공 복구 흐름을 유지한다. 최초 무통장의 카드 전환은 아래 경로를 사용한다. 같은 PG 거래는 한 번만 수단을 전환한다(methodRevision 0→1). 영구 차단 행을 다시 열거나 같은 식별자로 왕복 전환하지 않는다.

## 처리 순서

1. 브라우저 → Ticksy checkout/confirm: 같은 사용자·주문·좌석·checkout 키에 CREDIT_CARD를 요청한다.
2. Ticksy의 짧은 트랜잭션에서 소유권·미발급 READY·좌석 선점·대기열·판매 가능 여부를 확인한다. CheckoutAttempt의 기존 선점 기한을 최초 카드 기한으로 고정한다. gateway_switch_expires_at에 기한만 저장하고 gateway_switch_bank_company는 null이다. 카드→무통장 의도는 은행과 기한을 함께 갖기 때문에 방향이 섞이지 않는다.
3. PG ID를 아직 저장하지 못했다면 최초 무통장 은행·금액·입금기한으로 prepare를 재요청하고 기존 거래 ID를 바인딩한다. 카드 조건으로 새 prepare를 보내지 않는다.
4. Ticksy → PG POST /api/v1/payments/gateway/{pgPaymentId}/method/card. 인증된 가맹점·원거래·구매자·금액·최초 기한을 대조하고 TO_CARD_REQUESTED를 커밋한다.
5. PG → 은행 POST /internal/financial-simulations/banks/virtual-accounts/gateway/close-issue. 은행은 발급과 동일한 bank_account_issue_gates 행을 잠근다. 발급 사실이 있으면 closed=false와 최초 계좌를 반환한다. 계좌가 없으면 closed=true를 저장하고 향후 발급을 영구 차단한다.
6. 차단 성공 후 PG의 짧은 트랜잭션에서 같은 pgPaymentId의 수단을 CREDIT_CARD로 변경하고 은행을 제거하며 methodRevision을 증가시킨다. TO_CARD_COMPLETED 이력을 함께 저장한다.
7. Ticksy는 응답의 가맹점·PG ID·결제번호·구매자·금액·기한·수단·revision을 검증하고 Payment의 수단을 카드로 바꾼다. 은행·가상계좌 정보와 발급 대기 플래그를 제거한다. 브라우저는 기존 카드 세션 발급 → PG 카드 승인 경로를 이어간다.

## 경합과 실패

- 계좌 발급이 먼저 잠금을 얻으면 발급이 커밋된 뒤 전환이 거절된다. PG가 계좌 발급 응답을 못 받았더라도 은행의 최초 발급 사실이 우선이다.
- 차단이 먼저 커밋되면, PG 검증을 이미 통과했던 지연 발급도 은행 실행 단계에서 거절된다.
- 잠금 행의 최초 생성만 REQUIRES_NEW로 커밋한다. 생성은 INSERT 전용이며 동시 생성의 유니크 충돌은 기존 행을 재조회한다. CLOSED 행을 merge로 다시 열지 않는다.
- PG와 Ticksy는 외부 HTTP 동안 DB 잠금을 유지하지 않는다. 은행 차단·PG 변경·Ticksy 반영은 각각 독립 커밋이며 분산 원자성을 가정하지 않는다.
- 은행 응답 유실 또는 PG 변경 저장 실패 시 PG 수단은 그대로다. 은행 차단이 이미 커밋됐다면 늦은 발급은 막힌 상태이며 같은 요청이 이 차단 결과를 복구한다.
- PG 변경 성공 후 Ticksy 반영이 실패해도 같은 요청은 최초 PG 결과를 재사용한다. 새 결제번호나 키를 생성하지 않는다.
- 통신 실패·PG 5xx는 Ticksy에서 503 GATEWAY_METHOD_CHANGE_UNCONFIRMED로 재시도를 안내한다. 전환 HTTP의 자동 재시도·스케줄러·Outbox는 추가하지 않는다.
- 소유자·금액·최초 기한이 다른 요청은 같은 멱등 작업으로 수락하지 않는다. 기존 사용자 접근 제어와 가맹점 범위는 유지한다.
- 이미 지난 선점 기한을 늘리거나 만료·취소된 예매를 되살리지 않는다. 기존 계좌 취소·입금·환불 경로는 변경하지 않는다.

## 스키마와 기존 데이터

PG schema.sql에 bank_account_issue_gates를 추가했다. PG ID PK/FK, 최초 은행·금액·입금기한, closed, 생성·수정 시각을 저장한다. 현재 로컬 DB에는 이 테이블만 추가했으며 기존 데이터는 삭제하지 않았다. 별도 change.sql은 남기지 않는다.

기존 거래는 첫 발급·차단 요청에서 잠금 행을 생성한다. 행이 없다는 사실을 미발급으로 간주하지 않고 dummy_virtual_accounts의 실제 발급 사실을 확인한다. Ticksy는 기존 전환 조건 컬럼을 사용하므로 신규 컬럼이 필요 없다.

## 검증 결과

- 백엔드 754개 통과(common 45, PG 398, Ticksy 311). 실패·건너뜀은 없다.
- 은행 신규 통합 테스트 8개와 Ticksy 신규 전환 테스트 5개를 포함한다. 실제 PostgreSQL에서 발급·차단 경합, 최초 차단 행 동시 생성, 응답 유실 후 복구, PG 저장 실패 롤백, 변경된 요청 거절을 검증했다.
- 기존 카드→무통장 전환, 카드 승인·취소·환불, 가상계좌 발급·입금·취소·만료·환불, checkout 회귀도 통과했다. 기존 은행 발급의 요청 금액 불일치는 종전 400 계약을 유지한다.
- 프런트엔드 41개, 프런트엔드 빌드, PG·Ticksy bootJar 빌드가 통과했다. 실행 중인 로컬 PostgreSQL의 신규 테이블·컬럼·제약도 확인했다.
- 브라우저를 직접 조작하는 수동 결제 검증은 수행하지 않았다. 선점 기한이 지난 요청과 동일 거래의 반복 왕복 전환은 지원하지 않는다.

## Ticksy 전환 코드 구조

2026-11-17. 후보 조회와 방향별 서비스의 이름·처리 순서를 통일했다. HTTP 경로·요청/응답 계약·금융 정책·DB 스키마는 변경하지 않는다.

CheckoutService는 findMethodChangeCandidate를 한 번 호출해 저장된 수단과 요청 수단을 대조한다. 두 방향 모두 MethodChangeCandidate를 사용한다. 카드↔무통장 전환은 방향별 PG 전환 서비스로 보낸다. 실제 DB에 레거시 결제가 없음을 확인해 기존 미연결 카드의 별도 승인 이력 조회·호환 전환 분기는 제거했다. 같은 수단의 재요청과 다른 결제수단은 기존 confirm 경로를 따른다.

| 단계 | 카드→무통장 | 무통장→카드 |
|---|---|---|
| 서비스 | CardToBankCheckoutService | BankToCardCheckoutService |
| 준비 메서드 | prepareCardToBankChange | prepareBankToCardChange |
| 준비 레코드 | CardToBankPreparation | BankToCardPreparation |
| PG 연결 | prepareGatewayPayment | prepareGatewayPayment |
| 전환 HTTP | requestMethodChange | requestMethodChange |
| 결과 반영 | completeMethodChange | completeMethodChange |
| 로컬 결과 확정 | confirmCardToBank | confirmBankToCard |

로컬 결과 확정은 공통 CheckoutMethodChangeCommandService가 담당한다. 카드 승인 성공 복구·계좌 발급과 미발급 확인·발급 차단처럼 실제로 다른 처리는 방향별 서비스에 유지한다. 공통 상속 계층이나 불필요한 금융 도메인 통합은 추가하지 않는다.

구조 통일 후 checkout 회귀 테스트 142개(실제 PostgreSQL 통합 테스트 포함)가 전부 통과했고 실패·건너뜀은 없다. Ticksy bootJar 빌드도 통과했다.

2026-11-17 레거시 전환 정리: confirmLegacyMethodChange, MethodChangeCandidate.gatewayManaged, cardApprovalAbsent 인자, 일반 confirm 내부의 직접 카드→무통장 전환을 제거했다. PaymentGatewayCardClient의 미사용 승인 이력 조회 계약도 제거했다. 일반 confirm에서 저장된 수단과 요청 수단이 다르면 PG 전환 서비스를 이용하도록 거절한다. 신규 거래에서 PG ID 응답이 유실된 경우는 기존 방향별 서비스가 최초 prepare 조건으로 복구한다.

레거시 Checkout 전환 제거 후 checkout 회귀 테스트 140개가 전부 통과했다(실패·건너뜀 0). 삭제된 레거시 전환 전용 테스트를 정리하고 PG 전환 서비스 우회 방지 검증으로 교체했다. Ticksy bootJar 빌드도 통과했다. DB 데이터·스키마 변경은 없다.
