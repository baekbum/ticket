# Checkout 좌석 ID와 선점 소유권 검증

`SeatInfo`의 `id`와 `zone`·`row`·`col`이 같은 DB 좌석을 가리키는지 검증한다.
요청의 좌표만으로 Redis 소유권을 확인하고 요청의 ID만으로 예약을 생성하면,
A 좌석을 선점한 사용자가 A의 좌표와 B의 ID를 보내 B를 예약할 수 있다.

## 처리 흐름

1. `/api/v1/seat/occupy`: 기존 Redis 선점 API. 사용자와 주문에 연결된 선점은 10분간 유효하다.
2. `/api/v1/checkout/prepare`:
   - 빈 목록, null 항목, 0 이하 ID, 중복 ID를 거절한다.
   - 좌석 ID로 DB를 조회하여 공연 및 구역·열·번호가 요청과 같은지 확인한다.
   - DB에서 읽은 좌표로 Redis 선점 소유권과 유효시간을 확인한다.
   - 검증한 좌석 ID 집합을 `CheckoutAttempt.seatIds`에 저장한다.
   - 응답의 좌석 정보도 DB 조회 결과로 구성한다.
3. `/api/v1/checkout/confirm`:
   - 사용자·주문·공연과 함께 prepare의 좌석 ID 집합을 비교한다.
   - 목록의 순서는 무관하지만 중복·누락·추가는 거절한다.
   - DB에서 좌석을 다시 조회해 요청 좌표를 검증한다.
   - DB 좌표로 Redis 소유권·유효시간을 다시 확인한다.
   - 검증된 좌석 정보를 기존 예약 생성 경로에 전달한다.
   - 기존 비관적 락과 DB `AVAILABLE` 조건으로 예약 가능 여부를 확인한 뒤 좌석을 변경한다.

이미 확정된 요청을 같은 결제수단으로 재전송할 때는 저장된 ID 집합을 확인하고
기존 결제를 반환한다. Redis 선점이 만료되어도 동일한 결과를 조회할 수 있다.
카드에서 무통장으로 전환할 때도 좌석 ID 집합과 DB 좌표·Redis 소유권을 검증한다.

## 저장 구조

`checkout_attempt_seats`는 `CheckoutAttempt`의 `@ElementCollection`이다.
`(idempotency_key, seat_id)`가 복합 키이고, checkout 삭제 시 스냅샷도 삭제한다.
좌석 ID는 과거 선택의 스냅샷이므로 `seats`에 대한 외래 키는 추가하지 않는다.
스냅샷과 checkout 준비 정보는 같은 트랜잭션에 저장된다.

테이블 정의는 `ticket-service/src/main/resources/sql/schema.sql`에 포함되어 있다.

개발 DB는 앞선 테이블 초기화로 checkout·결제 데이터가 비어 있어 데이터 이전이나 복원이 필요 없다.
초기화 이후 추가한 `checkout_attempt_seats` 테이블만 생성해 사용한다.

좌석 스냅샷이 없는 비정상 checkout은 검증을 생략하지 않고 거절한다.
