# PG 거래 기반 은행 입금과 독립 Outbox 전달

2026-11-12 현재 Ticksy 신규 checkout은 prepare·PG ID 저장·신규 발급·취소 경로에 연결되어 있다. 만료도 [은행→PG Outbox와 Ticksy 조회](payment-gateway-account-expiration.md)로 연결했다. 아래 초기 단계의 미전환 범위 설명은 이 최신 내용으로 대체하며 신규 환불은 후속 작업이다.

2026-11-11 최신 구조: 입금은 BankController의 은행 업무 API다. POST /api/v1/banks/virtual-accounts/deposit에서 은행 상태·입금 이력·은행→PG Outbox를 함께 저장하고, PG는 수신 후 Ticksy에 별도 Outbox로 통보한다. Postman·Bruno도 같은 은행 API를 호출한다. 별도 테스트 컨트롤러와 support 입금 URL은 제거했다. 기존 PG 미연결 계좌는 POST /api/v1/banks/virtual-accounts/legacy/deposit를 사용한다. 인증은 기존 X-Payment-Provider-Token을 유지하며 PG→은행의 기존 내부 업무 URL과 금융 인증도 유지한다. [은행 API 상세](payment-gateway-bank-api.md)를 참고한다.

2026-11-10: 은행→PG와 PG→가맹점 전달을 각각 독립적인 Outbox로 처리한다. 은행의 최초 입금은 PG가 미리 판단하거나 동기 응답에서 확정하지 않는다. [취소 흐름](payment-gateway-account-cancellation.md)과도 분리한다.

## 진입점과 금융 책임

은행 입금 진입점 POST /api/v1/banks/virtual-accounts/deposit은 기존 X-Payment-Provider-Token 인증을 유지한다. BankController가 BankVirtualAccountDepositService를 직접 호출해 은행 입금·이력·은행→PG Outbox를 함께 커밋한 뒤 BankVirtualAccountDepositResponse를 반환한다. 이 응답은 은행 사실이며 pgStatus·가맹점 paymentNo·가맹점 notificationStatus를 포함하지 않는다. PG 입금 반영은 이후 독립적인 은행 Outbox 통보에서 진행된다.

은행 직접 입금 진입점도 같은 BankVirtualAccountDepositService를 사용한다. 은행은 계좌 행을 잠가 금액·상태·입금기한·최초 입금자/시각을 검증한다. BankVirtualAccountDepositService는 클래스의 @Transactional에 따른 기본 REQUIRED 전파를 사용한다. 입금 완료·은행 이력·BANK_DEPOSIT Outbox 저장은 같은 트랜잭션이며, 상위 트랜잭션이 있으면 참여하고 없으면 새로 시작한다. 저장 실패나 상위 트랜잭션 롤백 때 함께 롤백한다. 조회 find만 readOnly=true를 유지한다. 은행 도메인은 가맹점이나 Ticksy API를 호출하지 않는다.

기존 pg_payment_id가 NULL인 계좌의 API·입금 통보·이력은 기존 경로를 유지한다. 새 은행 Outbox에 가맹점이 불명확한 과거 자료를 임의 편입하지 않는다.

## 은행→PG

은행 Outbox 워커 BankDepositOutboxDispatcher가 고정된 BankDepositEvent를 HTTP로 보낸다. eventId는 pgPaymentId:DEPOSIT:INITIAL이다. 은행은 최초 계좌·금액·은행·입금자·입금 시각을 고정하며, 나중에 계좌가 취소돼도 이 입금 이벤트를 다른 내용으로 바꾸지 않는다.

수신 API는 POST /internal/financial-simulations/gateway/bank-deposits다. 현재 같은 앱 내 HTTP 금융 경계 전용 토큰을 사용한다. 일반 가맹점/내부 서비스 토큰만으로 호출할 수 없다. 요청 주소는 서버 설정 app.bank.gateway-base-url이며 비어 있으면 실제 실행 포트의 loopback을 사용한다. 일반 결제 요청에 주소를 받지 않는다.

GatewayVirtualAccountDepositCommands도 클래스의 @Transactional로 기본 REQUIRED 전파를 사용한다. 상위 트랜잭션이 있으면 참여하고 없으면 수신 처리에서 새로 시작한다. confirm에서는 PG 원거래를 잠그고 저장된 수단·은행·금액·계좌·기한과 최초 입금 사실을 검증한다. PG 상태/paidAt·DEPOSIT/INITIAL 이력·gateway_bank_notification_outbox를 같은 PG 트랜잭션에 커밋한 뒤 BankDepositAcknowledgement를 HTTP 200으로 반환한다. 기존 입금이면 같은 사실을 대조하고 새 이력·Outbox 없이 확인한다. 발급 확인만 유실된 READY 거래는 발급 이력도 함께 복구한다.

은행은 이벤트/PG/계좌/금액/시각이 일치하는 PG 커밋 확인을 받아야 은행 Outbox를 DELIVERED로 바꾼다. PG 저장 실패·응답 유실·5xx·202·잘못된 확인은 같은 이벤트로 재시도한다. PG가 커밋 후 응답만 유실된 경우 재전달도 최초 사실의 멱등 확인을 반환한다. 여기서 은행→PG 전달은 종료되며 가맹점 통보 결과를 기다리지 않는다.

## PG→가맹점

GatewayBankNotificationDispatcher는 기존 gateway_bank_notification_outbox에서 60초 임대를 커밋한 후 등록 merchant_notification_endpoints의 URL·복호화 토큰으로 BankDepositNotification을 보낸다. 임의 주소·가맹점별 Java 클래스·YAML 대상 목록은 사용하지 않는다. 기본 계약은 PAYMENT_NOTIFICATION_V1이며 기존 CARD_NOTIFICATION_V1 호환도 유지한다.

Ticksy의 공통 수신 URL은 POST /api/v1/payments/internal/gateway/notifications다. 은행 입금 DTO에는 최초 이벤트/가맹점/PG/가맹점 번호/소유자/금액/계좌/은행/입금자/시각이 포함된다. Ticksy는 기존 소유자 검증·입금 완료·예매/티켓/좌석 규칙과 멱등 이력을 적용한 뒤 APPLIED 또는 RESERVATION_CANCELLED를 반환한다. PG는 응답의 거래/이벤트/계좌/금액까지 확인한 후 가맹점 Outbox를 DELIVERED로 바꾼다.

가맹점 실패는 이 Outbox만 재시도하며 은행 입금이나 은행→PG 전달 완료를 되돌리지 않는다. 기존 카드 전용 통보 URL과 입금 완료 API도 호환 경로로 남긴다.

## 경합·복구와 자금 반환

같은 최초 입금의 중복·동시 요청은 은행 계좌 잠금과 PG 거래 잠금, 각 작업 유니크 제약으로 하나의 입금/이력/Outbox에 수렴한다. 같은 키에 다른 금액·입금자·시각은 거절한다. 시각 생략 재요청은 이미 확정된 최초 시각을 사용한다.

취소된 PG에 입금 이벤트가 늦게 도착하면 최초 입금 사실은 기록하지만 CANCELLED를 PAID로 되돌리지 않는다. Ticksy가 취소 예매임을 확인하면 기존 reject-deposit 은행 정리 HTTP 후 PG 취소 이력·가맹점 입금 Outbox 완료를 함께 반영한다. 최초 금액·시각·입금자·이력은 유지한다. 이 계좌 취소는 실제 자금 반환이 아니며 fundsReturned=false다.

HTTP 동안 은행/PG/Outbox 락을 유지하지 않는다. 각 단계는 실패 후 30초 재시도, 60초 임대 만료 회수, attempt로 지연 워커의 덮어쓰기 방지를 적용한다. 금융 성공과 전달 실패는 다른 상태다. PG가 은행 테이블과 PG 이력을 직접 대사하던 recoverDeposits는 제거했다. 기존 은행 입금 중 새 통보 Outbox가 없는 행은 은행 저장소만 조회해 한 번 편입한다.

## 스키마·호환성

은행→PG 통보는 bank_transfer_outbox의 BANK_DEPOSIT 행이다. PG→가맹점 입금 통보는 기존 gateway_bank_notification_outbox다. 취소의 두 전달 단계와는 별도 행/상태/재시도다. 기존 테이블 삭제·초기화는 없다. PG와 Ticksy schema.sql, 추가 전용 SQL의 상세는 취소 문서를 참고한다.

현재 Ticksy checkout은 기존 발급 API를 사용한다. prepare·pgPaymentId 저장·새 호출 전환과 PG 연결 만료/환불은 후속 범위다. 늦은 입금의 실제 반환 기능은 구현하지 않는다.

## 최종 검증 (2026-11-11)

동일 회귀 명령의 최종 실행이 BUILD SUCCESSFUL로 완료됐다. 공통 45개, PG 311개, Ticksy 130개로 총 486개가 통과했고 실패·오류는 0개다. 선택 환경 변수 CHECKOUT_TEST_POSTGRES_URL이 없는 기존 CheckoutPrepareRetryIntegrationTest 11개만 조건부 건너뜀이다. 실제 PostgreSQL의 PG 테스트 23개는 모두 실행해 통과했다.

검증에는 은행 입금/PG 통보 Outbox의 독립 완료, 가맹점별 동일 결제번호, 다른 가맹점 접근 차단, 같은 키의 다른 내용 거절, 중복·동시 취소, 입금/취소 경합, 은행·PG·가맹점 응답 유실, 은행 이력/Outbox/멱등 결과 저장 실패 롤백, PG 이력/다음 Outbox 저장 실패 롤백, 임대 회수·이전 워커 무시, 취소 후 지연 입금의 사실 보존, 표준 헤더/DTO의 실제 HTTP 전송, 가맹점 수신 이력과 재응답, 기존 카드·가상계좌·환불·예매·좌석·checkout·Feign 회귀를 포함한다.

실제 local-postgres/ticket DB에는 추가 전용 DDL로 3개 테이블을 생성했다. public 테이블은 58개에서 61개가 됐고, 기존 가맹점 1건과 결제 데이터는 유지됐다. 검증 후 PG/Ticksy 결제와 신규 Outbox/수신 이력은 모두 0건이다. 실제 DB 검증은 DDL validate와 롤백을 사용했고 실제 테이블 삭제·초기화는 수행하지 않았다. 빌드가 변경한 기존 Querydsl 13개 파일은 원본과 실질 내용이 같음을 확인한 후 EOF만 복원했으며 새 수신 이력 Q 클래스는 보존했다. git diff --check가 통과했다.

배포 시 추가 스키마와 Ticksy의 새 결과 수신 처리를 먼저 반영한 뒤 PG 워커를 배포한다. 기존 Ticksy checkout의 PG ID 저장·새 API 호출 전환과 PG 연결 만료/환불은 후속 범위다.
