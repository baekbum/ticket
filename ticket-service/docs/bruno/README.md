# ticket-service Bruno API 테스트

Bruno에서 아래 폴더를 열면 됩니다.

```text
ticket-service/docs/bruno
```

환경은 실행 방식에 맞춰 선택합니다.

- `local-direct`: ticket-service를 `8082` 포트로 직접 호출할 때 사용
- `local-ingress`: 로컬 ingress의 `80` 포트를 통해 호출할 때 사용

사용 순서:

1. `auth-service/docs/bruno`에서 `Admin Login` 또는 `User Login`을 먼저 실행합니다.
2. 발급된 `accessToken`을 이 컬렉션의 선택한 환경에 붙여 넣습니다.
3. `eventId`, `seatId`, `couponId`, `userCouponId` 같은 환경변수를 현재 DB 데이터에 맞게 조정합니다.
4. 조회 요청부터 실행하고, 생성/수정/취소 요청은 body 값을 확인한 뒤 실행합니다.

주요 흐름:

1. `manage-coupon / Issue Coupon`으로 사용자 쿠폰을 발급합니다.
2. `manage-seat / Cache Warm Up Event`로 좌석 캐시를 준비합니다.
3. `seat / Occupy Seat`로 좌석 점유를 확인합니다.
4. `checkout / Prepare Checkout`으로 예매, 배송, 할인 스냅샷, 결제를 생성합니다.
5. 응답의 `paymentNo`, `reservationId`를 환경변수에 반영합니다.
6. `payment / Confirm Payment`로 결제 완료를 처리합니다.
7. `manage-reservation / Select Reservation Detail`로 예매 상세를 확인합니다.

주의:

- 삭제, 취소, 배송 상태 변경 요청은 실제 데이터를 변경합니다.
- 공연 등록과 SVG 구역 등록은 multipart 요청입니다. Bruno에서 직접 요청을 추가할 때 `event` 또는 `svgFile` form field를 맞춰 사용하세요.
- `Prepare Checkout`은 좌석 상태, 쿠폰 상태, 사용자 구매 제한의 영향을 받습니다. 실패하면 먼저 좌석/쿠폰/캐시 상태를 확인하세요.
