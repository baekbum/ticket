# 가상계좌 만료 이벤트 소비

기존 계좌의 발급 재요청도 계좌를 잠근 뒤 만료 여부를 확인한다. 이 경로에서 만료를 확정하면 스케줄러와 마찬가지로 만료 이력과 Outbox를 함께 저장한다. 따라서 계좌만 `EXPIRED`로 바뀌어 티켓에 만료 알림이 누락되는 경우를 방지한다.

발급된 무통장 계좌의 만료는 PG에서 확정한 이 이벤트로 반영한다. 티켓의 자체 만료 스케줄러는 `BANK_TRANSFER`·`WAITING_DEPOSIT`을 제외하여, 기한 안에 입금됐지만 [입금 알림](virtual-account-deposit-outbox.md)이 지연된 결제의 좌석을 먼저 해제하지 않는다.

PG 만료 스케줄러의 기본 실행 시각은 매일 서버 시간대 기준 자정(`0 0 0 * * *`)이다. `23:59:59`에 입금 기한이 끝난 계좌를 다음 날 자정부터 만료 대상으로 처리한다.

PG 만료 스케줄러는 만료 대상 계좌를 `PESSIMISTIC_WRITE`로 조회한다. 입금 처리도 같은 계좌 행을 잠그므로, 먼저 잠근 트랜잭션이 끝날 때까지 다른 처리는 대기한다. 입금이 커밋되면 만료 조회의 `WAITING_DEPOSIT` 조건에서 제외되고, 입금이 롤백되면 만료 대상으로 처리할 수 있다. 만료를 먼저 커밋했다면 이후 입금은 `EXPIRED` 상태를 확인해 거부한다.

잠금을 획득한 계좌의 상태와 만료 시간을 확인한 뒤 계좌 만료·만료 이력·Outbox를 같은 트랜잭션에서 저장한다. 만료 트랜잭션이 롤백되면 세 변경도 함께 취소된다. 여러 스케줄러가 동시에 실행되어도 이미 만료된 계좌에 이력과 Outbox를 다시 생성하지 않는다. 잠금은 기존 스케줄러 트랜잭션이 종료될 때 해제된다.

`VirtualAccountExpirationConcurrencyTest`는 입금 커밋·롤백, 만료 후 입금 조회, 스케줄러 동시 실행, 만료 전체 롤백을 검증한다. 기본값은 H2이며, `GATEWAY_TEST_POSTGRES_PORT`를 설정하면 해당 로컬 포트의 검증 전용 PostgreSQL DB `gateway_expiration_test`를 사용한다(계정·비밀번호: `gateway_test`). 테스트가 테이블을 생성·삭제하므로 별도의 임시 컨테이너에서 실행한다.

payment-gateway-service는 `virtual-account-expired`에 JSON을 발행하며 Java 타입 헤더는 넣지 않는다. ticket-service의 해당 리스너는 공통 `eventKafkaListenerContainerFactory`를 사용한다. `TicketKafkaValueDeserializer`가 토픽별 타입 매핑을 조회해 `VirtualAccountExpiredEvent`로 역직렬화한다. 타입 헤더가 있더라도 이 토픽의 이벤트 타입을 변경하지 않는다.

타입 매핑은 `KafkaConsumerConfig`의 `eventTypes`에서 관리한다. 토픽 이름은 `topic.payment.virtual-account.expired.name` 설정값을 사용한다. 같은 소비 정책을 사용하는 새 이벤트는 이 매핑에 토픽과 이벤트 클래스를 추가하고, 리스너에서 같은 공통 팩토리를 지정한다. 등록되지 않은 토픽은 역직렬화 오류로 처리하며 임의 타입으로 변환하지 않는다. 공통 팩토리를 사용해도 각 리스너의 컨테이너와 소비자 그룹은 별개다. 재시도·트랜잭션·배치 처리 정책이 다르면 별도 팩토리를 사용할 수 있다.

새 이벤트를 이 공통 팩토리에 연결할 때는 DLT 발행도 확인한다. 현재 `TicketKafkaValueSerializer`는 문자열, 원본 바이트, `VirtualAccountExpiredEvent`를 지원하므로 다른 이벤트 객체의 업무 실패를 DLT로 발행하려면 해당 이벤트 타입도 직렬화기에 등록해야 한다.

`ErrorHandlingDeserializer`가 JSON 역직렬화 실패를 레코드 오류로 전달하므로 기존 DLT 오류 처리기가 실패한 레코드를 처리할 수 있다. DLT 발행 시 역직렬화 실패의 원본 `byte[]`와 정상 역직렬화 후 업무 처리에 실패한 이벤트 객체를 모두 직렬화한다. 기존 `payment-completed` JSON 문자열은 추가 JSON 인코딩 없이 그대로 발행한다. DLT 발행 실패는 오류로 전파해 레코드를 처리 완료로 넘기지 않는다.

`No type information in headers and no default type provided`는 이 토픽에서 생산자와 소비자의 타입 설정이 달랐다는 뜻이다. 수정된 ticket-service를 다시 실행하면 아직 처리하지 못한 오프셋부터 재소비한다. 기존 메시지가 정상 JSON이면 헤더 없이도 읽으며, 실제 JSON 오류나 업무 오류는 DLT에서 확인한다. 이 오류를 해결하기 위해 운영 메시지를 삭제하거나 소비자 오프셋을 강제로 건너뛸 필요는 없다.
