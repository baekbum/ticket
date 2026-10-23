# 가상계좌 입금 알림 Outbox

PG 계좌는 입금 사실만 관리한다. 입금 시 계좌 행을 비관적으로 잠그고 `DEPOSITED`, 입금 이력, `virtual_account_deposit_outbox`의 `PENDING` 알림을 같은 트랜잭션에서 커밋한다. 입금 커밋이 실패하면 티켓을 호출하지 않는다. 동일 계좌의 동일한 입금 재요청은 이력과 알림을 추가하지 않으며, 금액·입금자·명시된 입금 시각이 다르면 거부한다.

`VirtualAccountDepositOutboxService`는 `MANDATORY`로 입금 트랜잭션에 참여하고 `afterCommit`에 최초 전달을 등록한다. `VirtualAccountDepositOutboxPublisher`는 별도의 `REQUIRES_NEW` 트랜잭션으로 Outbox 행만 잠가 전달한다. 계좌 행의 잠금은 입금 커밋 때 끝나므로 티켓 응답을 기다리는 동안 계좌를 계속 잠그지 않는다. 여러 PG 인스턴스가 같은 알림을 선택해도 Outbox 행 잠금과 전달 완료 상태 검사로 중복 호출을 제어한다.

티켓은 결제번호로 결제를 잠그고 은행·계좌·금액을 확인한다. 최초 요청은 입금 시각이 기한 안인지 확인한 뒤 결제·예매·티켓·좌석을 기존 트랜잭션에서 함께 반영한다. 이미 `PAID`, `PARTIALLY_REFUNDED`, `REFUNDED`라면 최초 입금자·입금 시각까지 일치하는 경우 현재 결과를 반환하며 완료 이력을 다시 만들지 않는다. PG는 응답의 결제번호·수단·금액·은행·계좌·입금자·입금 시각과 완료 상태를 확인하고 Outbox만 `DELIVERED`로 바꾼다. PG 계좌는 계속 `DEPOSITED`다.

응답 유실, HTTP 오류, 응답 불일치는 `PENDING`을 유지하고 실패 횟수·오류 유형·다음 시도 시각을 저장한다. 기본 재시도 간격은 30초이며 횟수 제한은 없다. 티켓 성공 후 PG의 전달 완료 커밋이 실패해도 기존 `PENDING`이 남아 같은 요청을 재전달한다. 커밋 직후 서버가 종료되어 최초 전달을 실행하지 못한 경우도 스케줄러가 복구한다. 조회는 `status,next_attempt_at,outbox_id` 인덱스로 다음 시도 시각이 된 알림만 최대 100건 선택한다.

발급된 무통장 계좌의 `WAITING_DEPOSIT`은 티켓의 로컬 시계만으로 자동 만료하지 않는다. 기한 내 입금 알림이 늦게 도착해도 처리할 수 있도록 PG의 만료 이벤트로 만료를 확정한다. 이미 취소·만료된 결제는 알림만으로 되살리지 않는다. 그런 거절 응답도 Outbox에 남겨 관리자 확인과 원인 조치 후 재시도할 수 있다.

설정은 `app.virtual-account.deposit-outbox.fixed-delay-ms`, `retry-delay-ms`, `batch-size`다. Feign의 연결·응답 타임아웃은 기존 `ticket-payment-client` 설정을 따른다. 입금 알림은 HTTP로, 기존 만료 이벤트는 Kafka로 전달하며 서로 다른 Outbox를 사용한다.

기존 DB에는 PG를 중지한 상태에서 `payment-gateway-service/src/main/resources/sql/add-virtual-account-deposit-outbox.sql`을 적용한 뒤 새 버전을 실행한다. 이 SQL은 테이블·인덱스를 만들고 기존 입금 완료·연동 실패 건의 알림을 채운다. 이전 `TICKET_PAYMENT_COMPLETED`는 `DELIVERED`, 나머지 입금 건은 `PENDING`으로 보존한 뒤 계좌 상태를 `DEPOSITED`로 정리한다. 기존 상태·이력 enum의 티켓 연동 값은 제거했다. 해당 타입의 과거 이력이 남아 있으면 SQL이 중단되므로 별도 보관·정리한 뒤 적용한다. 이력은 임의 삭제하거나 실제 입금 이력으로 바꾸지 않는다. 입금 시각·입금자 정보가 없는 과거 행은 자동 이관하지 않는다.

`VirtualAccountDepositOutboxIntegrationTest`는 기본 H2에서 실행한다. `GATEWAY_DEPOSIT_TEST_POSTGRES_PORT`를 설정하면 로컬 검증 전용 PostgreSQL DB `gateway_deposit_test`(계정·비밀번호 `gateway_test`)를 사용한다. 테이블을 생성·삭제하므로 임시 컨테이너에서 실행한다. 입금 커밋 전 미호출, 커밋 실패 롤백, 응답 유실, 최초 전달 누락, 전달 완료 커밋 실패, 응답 검증, 동시 전달을 검증한다. 티켓 측 실제 JPA 반영·환불 이후 재전달·이력 중복 방지는 `PaymentModelIntegrationTest`에서 검증한다.
