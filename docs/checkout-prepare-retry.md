# Checkout prepare 재시도 복구

`orderId`는 준비 요청의 복구 기준이고, 최초 준비에서 발급한 `idempotencyKey`는 기존처럼 confirm에서 사용한다.

## 처리 흐름

1. 주문 번호와 좌석 ID 목록을 검증하고, DB에서 좌석의 공연·좌표를 확인한다.
2. Redis에서 좌석 선점 소유권과 INSERT에 필요한 만료 시각을 확인한다.
3. `insertIfAbsent()`로 준비 건을 저장한다. `ON CONFLICT (order_id) DO NOTHING`을 사용하여 신규 주문은 1, 기존 주문은 0을 반환한다.
4. INSERT에 성공한 경우에만 `checkout_attempt_seats`에 좌석 목록을 저장한다. 부모와 좌석은 같은 트랜잭션에 참여한다.
5. `orderId`로 준비 건을 비관적 쓰기 잠금으로 조회한다.
6. 인증된 사용자·주문·공연·좌석이 일치하고 PREPARED 상태이며 만료되지 않았는지 확인한다.
7. 공연 읽기 잠금 아래 판매 기간·공연 상태와 활성 대기열 토큰을 검증한다.
8. INSERT를 기다리는 동안 선점이 만료되거나 소유권이 바뀌었을 수 있으므로 Redis 좌석 선점을 다시 확인한다.
9. 응답 직전에 준비 만료와 판매 기간을 다시 확인하고, DB에서 읽은 키·준비 시각·만료 시각으로 반환한다.

좌석 응답은 ID 순으로 정렬하므로 같은 좌석을 다른 순서로 보내도 같은 결과를 반환한다.
재시도 시 새로 만든 후보의 키와 결제 번호는 저장되지 않으며, 기존 준비 건의 만료 시각도 연장하지 않는다.
확정되었거나 만료된 준비 건은 재사용하지 않는다.

## 동시 요청과 트랜잭션

같은 주문의 A 요청이 INSERT에 성공하면, B 요청은 자신의 INSERT에서 A의 트랜잭션이 끝날 때까지 기다린다.
A가 커밋하면 B의 INSERT는 0을 반환하고, B는 커밋된 기존 준비 건과 좌석 목록을 잠금 아래 조회한다.
A가 롤백하면 B가 직접 INSERT에 성공하여 1을 반환하고 자신의 좌석 목록을 저장한다.

`prepare` 전체는 하나의 `@Transactional` 트랜잭션으로 실행한다.
중복을 예외로 처리하거나 실패 후 별도 트랜잭션으로 재조회하지 않는다.
요청 검증이나 좌석 저장에 실패하면 부모와 좌석 목록을 모두 롤백한다.
충돌 대상을 `order_id`로 한정하므로 다른 고유 제약이나 NOT NULL 등의 오류는 그대로 전파한다.
준비 건 잠금 이후 공연 읽기 잠금을 획득하여 confirm과 잠금 순서를 맞춘다.

PostgreSQL의 READ COMMITTED 격리 수준을 전제로 한다.
`ON CONFLICT`와 좌석 INSERT SQL은 `CheckoutAttemptInsertRepositoryImpl` 안에 둔다.
DB를 교체할 때는 해당 Repository 구현을 변경해야 한다.
네이티브 INSERT는 JPA의 컬렉션 자동 저장을 실행하지 않으므로 신규 준비 건의 좌석 목록도 Repository에서 명시적으로 저장한다.

준비 시각은 엔티티 생성 시 `createdAt`에 기록하며 준비·만료 시각은 마이크로초로 맞춘다.
네이티브 INSERT 시 `updatedAt`도 준비 시각으로 초기화한다.
테이블과 API 스키마는 변경하지 않는다.

## 검증

`CheckoutServiceTest`는 재사용·요청 불일치·만료·대기열 토큰 및 INSERT 후 선점 재검증을 검사한다.
`CheckoutPrepareRetryIntegrationTest`는 `CHECKOUT_TEST_POSTGRES_URL`로 지정한 분리된 PostgreSQL에서 다음을 검사한다.

- 응답 유실 후 같은 키·시각·좌석으로 복구.
- 같은 주문의 실제 INSERT 대기와 동일 결과 반환.
- 먼저 삽입한 요청이 롤백되면 대기 요청이 직접 생성.
- 다른 주문의 병렬 처리.
- 좌석 저장 또는 후속 검증 실패 시 부모·좌석 데이터 롤백.
- INSERT 결과 1/0과 기존 좌석 목록 보존.
- `payment_no` 등 다른 고유 제약 오류 전파.

PostgreSQL 전용 INSERT를 검증하므로 이 통합 테스트는 해당 환경 변수를 지정한 경우 실행한다.
