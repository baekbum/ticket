# 환불 요청 멱등성

2026-11-18 현재 계약. 구형 paymentNo 전용 환불 실행·resolve API와 gateway_refunds는 제거했다. 은행·카드의 공통 PG 진입점은 GatewayPaymentRefundController다. 세부 API·응답은 [은행 환불](payment-gateway-bank-refund.md)과 [카드 환불](payment-gateway-card-refund.md)을 따른다.

## 최초 신청과 재시도

Ticksy는 예매의 NOWAIT 잠금 아래 PaymentRefundSnapshot과 대상 티켓·가격·수수료·환불액·계좌 조건을 저장하고 PaymentRefundProcess를 만든다. 준비 트랜잭션은 외부 요청 전에 커밋한다. 실행 시 예매를 다시 잠그고 최신 상태를 확인한다.

동일 대상·조건의 재요청은 최초 신청 ID와 스냅샷을 재사용한다. 완료된 요청도 최초 성공을 반환한다. 미완료 신청과 조건이 다르면 기존 신청 정보를 포함한 409로 거절하며 자동 대체하지 않는다. 다른 좌석의 새 신청은 기존 신청의 완료 또는 명시적 철회가 확인된 뒤 허용한다. SUPERSEDED 상태는 제거했다.

Ticksy의 paymentRefundProcessId를 PG의 refundRequestId로 전달한다. PG는 인증된 가맹점의 저장 원거래를 조회하고 결제수단을 선택한다. paymentNo만으로 환불이나 가맹점 권한을 식별하지 않는다.

## PG와 금융 결과

PG는 gateway_bank_refunds 또는 gateway_card_refunds에 최초 조건과 REQUESTED/SUCCEEDED/WITHDRAWN 결과를 저장한다. 키는 UNIQUE(pg_payment_id, refund_request_id)다. 금융 결과는 bank_virtual_account_refunds 또는 card_payment_refunds에 같은 거래·신청 범위로 저장한다.

같은 키의 다른 금액·계좌 등은 거절한다. 금액은 통화 소수 자릿수에 맞춰 정규화하며 재시도에서 조건이나 키를 새로 만들지 않는다. 금융 원거래 잠금 아래 누적 환불·한도 복원·이력·확정 결과를 함께 커밋한다. PG도 자체 확정 트랜잭션으로 상태·환불액·이력·결과를 함께 커밋한다.

Ticksy→PG는 자동 재시도하지 않는다. PG→은행·카드는 같은 요청으로 최초 1회와 추가 3회까지 시도한다. 연결·응답 유실, 5xx, 해석 불가·불일치 결과는 재시도하며 확정 4xx는 즉시 거절한다. 네 번 모두 결과를 확인하지 못하면 503 결과 미확인으로 안내하고 사용자가 같은 신청을 다시 요청한다. 사용자 환불 전용 Outbox·자동 실행 스케줄러는 없다.

금융만 성공했거나 PG 성공 응답이 유실돼도 같은 키로 최초 결과를 재생한다. Ticksy 로컬 반영만 실패한 경우 확인된 금융 성공을 보존하고 로컬 완료를 복구한다. HTTP를 넘는 공동 롤백은 가정하지 않는다.

## 환불 신청 철회와 경합

철회는 이미 지급한 환불을 되돌리는 기능이 아니다. Ticksy는 WITHDRAW_REQUESTED를 먼저 커밋하고 최초 조건으로 철회를 요청한다. 금융 환불이 먼저 성공했다면 최초 SUCCEEDED를 반환하여 PG·Ticksy 완료를 복구한다. 미실행 요청은 금융 측 WITHDRAWN 기록으로 지연 실행을 차단한다. 철회 결과가 확인되지 않으면 새 신청을 차단하고 동일 철회를 재확인한다.

사용자 환불 실행·철회는 Ticksy Reservation의 FOR UPDATE NOWAIT 잠금을 PG HTTP와 로컬 반영 완료까지 유지한다. 같은 예매의 다른 요청은 기다리지 않고 409 RESERVATION_PROCESSING을 반환한다. PG는 금융 HTTP 동안 원거래 잠금을 유지하지 않고 전후 짧은 트랜잭션을 사용한다. 금융 도메인은 원거래 잠금으로 환불과 철회를 직렬화한다.

## 저장·검증

최종 DDL은 각 서비스의 schema.sql을 따른다. 구형 모델 삭제와 데이터 보호 범위는 [모델·설정 정리 기록](payment-legacy-model-cleanup.md)에 기록한다. 초기화 SQL 전체를 기존 DB에 실행하지 않는다. 환불 계좌 암호화 키와 가맹점 자격증명은 소스·DDL에 넣지 않는다.

현재 회귀 테스트는 동일 키 재전달·조건 불일치·가맹점 격리·부분 환불·철회 경합·응답 유실·저장 실패 롤백·Ticksy NOWAIT 충돌을 검증한다. PostgreSQL을 사용하는 테스트와 실제 DB 스키마 확인 결과는 정리 기록에 남긴다.
