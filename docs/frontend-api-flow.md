# Frontend API Flow

이 문서는 사용자 프론트 화면에서 API를 호출하는 순서를 정리한 계약 초안이다.
현재 별도 프론트 서버가 없으므로, 경로는 각 백엔드 서비스의 내부 API 경로(`/api/v1/...`) 기준으로 적는다.
나중에 프론트 서버나 Gateway/BFF가 생기면 Base URL만 해당 환경에 맞게 치환한다.

## 공통 규칙

### 인증 헤더

로그인 이후 대부분의 사용자 API는 Access Token을 보낸다.

```http
Authorization: Bearer {accessToken}
```

Refresh Token은 재발급과 로그아웃에서 별도 헤더로 보낸다.

```http
Authorization-Refresh: Bearer {refreshToken}
```

대기열 통과 이후 좌석 점유와 체크아웃 준비에는 Queue Token을 보낸다.

```http
X-Queue-Token: {queueToken}
```

### 프론트 저장 값

프론트는 최소한 아래 값을 저장해야 한다.

| 값 | 출처 | 사용처 |
| --- | --- | --- |
| `accessToken` | `POST /api/v1/login` | 인증 API 호출 |
| `refreshToken` | `POST /api/v1/login`, `POST /api/v1/reissue` | 토큰 재발급, 로그아웃 |
| `eventId` | 이벤트 목록/상세 | 구역, 좌석, 대기열, 체크아웃 |
| `queueToken` | 대기열 `READY` 응답 | 좌석 점유, 체크아웃 준비 |
| `orderId` | 좌석 점유 응답 | 체크아웃 준비 |
| `paymentNo` | 체크아웃 준비 응답 | 결제 확정 |
| `reservationId` | 체크아웃 준비/결제 응답 | 예약 상세, 티켓 조회 |

### 토큰 만료 처리

사용자 API에서 `401`이 오면 프론트는 Refresh Token으로 재발급을 시도한다.

```http
POST /api/v1/reissue
Authorization-Refresh: Bearer {refreshToken}
```

재발급 성공 시 새 `accessToken`, `refreshToken`을 저장하고 원 요청을 1회 재시도한다.
재발급 실패 시 저장된 토큰을 삭제하고 로그인 화면으로 이동한다.

## 화면별 호출 순서

### 1. 회원가입

아이디 중복 확인이 필요하면 먼저 호출한다.

```http
GET /api/v1/check/duplication/{userId}
```

회원가입 요청:

```http
POST /api/v1/signup
Content-Type: application/json
```

회원가입 직후 자동 로그인할지, 로그인 화면으로 보낼지는 프론트 정책으로 정한다.

### 2. 로그인

```http
POST /api/v1/login
Content-Type: application/json
```

요청:

```json
{
  "userId": "user01",
  "password": "user123!"
}
```

응답:

```json
{
  "accessToken": "...",
  "refreshToken": "..."
}
```

프론트 처리:

1. `accessToken`, `refreshToken` 저장
2. 메인 또는 이벤트 목록 화면으로 이동

### 3. 로그아웃

```http
POST /api/v1/logout
Authorization-Refresh: Bearer {refreshToken}
```

성공 응답은 `204 No Content`다.
성공/실패와 관계없이 프론트는 로컬 토큰을 삭제하고 로그인 화면으로 이동해도 된다.
백엔드는 Redis의 `RT:{userId}`를 삭제해 Refresh Token을 폐기한다.

### 4. 이벤트 목록

```http
GET /api/v1/event/select?page=0&size=10
Authorization: Bearer {accessToken}
```

주요 쿼리 파라미터:

| 파라미터 | 설명 |
| --- | --- |
| `page` | 페이지 번호, 기본 0 |
| `size` | 페이지 크기, 기본 10 |
| `title` | 공연 제목 검색 |
| `artistName` | 아티스트 검색 |
| `venue` | 장소 검색 |
| `eventDateFrom`, `eventDateTo` | 공연일 범위 |
| `status` | 이벤트 상태 |
| `sort` | 정렬 조건 |

응답은 `CustomPageResponse<EventResponse>` 형태다.
목록 카드에서 `eventId`, 제목, 포스터, 공연일, 장소, 잔여 좌석 등을 사용한다.

### 5. 이벤트 상세

```http
GET /api/v1/event/select/id/{eventId}
Authorization: Bearer {accessToken}
```

프론트 처리:

1. 이벤트 상세 정보 표시
2. 예매 가능 상태인지 확인
3. 좌석/구역 선택 화면 진입 버튼 노출

### 6. 구역/좌석 조회

이벤트의 전체 배치도:

```http
GET /api/v1/area/layout/event/{eventId}
Authorization: Bearer {accessToken}
```

이벤트의 구역 목록:

```http
GET /api/v1/area/select?eventId={eventId}&page=0&size=100
Authorization: Bearer {accessToken}
```

특정 구역 또는 이벤트의 좌석 목록:

```http
GET /api/v1/seat/select?eventId={eventId}&areaId={areaId}&page=0&size=100
Authorization: Bearer {accessToken}
```

좌석 조회 주요 쿼리 파라미터:

| 파라미터 | 설명 |
| --- | --- |
| `eventId` | 이벤트 ID |
| `areaId` | 구역 ID |
| `zone` | 구역/존 코드 |
| `seatRow` | 좌석 행 |
| `seatCol` | 좌석 열 |
| `grade` | 좌석 등급 |
| `status` | 좌석 상태 |

프론트 처리:

1. 배치도와 구역 목록 렌더링
2. 구역 선택 시 좌석 목록 조회
3. 사용자가 좌석을 선택하면 `SeatInfo` 배열을 만든다

`SeatInfo` 형식:

```json
{
  "id": 1,
  "zone": "A",
  "row": 1,
  "col": 10
}
```

### 7. 대기열 진입

좌석 점유 또는 체크아웃 전에 대기열을 통과해야 한다.

```http
POST /api/v1/queue/events/{eventId}/enter
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "eventId": 1,
  "status": "WAITING",
  "rank": 10,
  "waitingCount": 100,
  "token": null,
  "expiresInSeconds": null
}
```

`status`가 `READY`이면 `token`이 내려온다.
프론트는 이 값을 `queueToken`으로 저장한다.

### 8. 대기열 상태 폴링

```http
GET /api/v1/queue/events/{eventId}/status
Authorization: Bearer {accessToken}
```

프론트 처리:

1. `status === "WAITING"`이면 `rank`, `waitingCount`를 화면에 표시하고 일정 간격으로 재호출
2. `status === "READY"`이면 `token` 저장 후 좌석 선택/점유 단계로 이동
3. `expiresInSeconds`가 있으면 Queue Token 만료 시간을 UI 또는 내부 타이머에 반영

### 9. 좌석 점유

```http
POST /api/v1/seat/occupy
Authorization: Bearer {accessToken}
X-Queue-Token: {queueToken}
Content-Type: application/json
```

요청:

```json
{
  "eventId": 1,
  "seats": [
    {
      "id": 101,
      "zone": "A",
      "row": 1,
      "col": 10
    }
  ],
  "maxTicketsPerPerson": 4
}
```

주의:

`userId`는 백엔드가 Access Token에서 추출해 세팅하므로 프론트에서 보내지 않는다.

응답:

```json
{
  "orderId": "...",
  "eventId": 1,
  "userId": "user01",
  "seats": [
    {
      "id": 101,
      "zone": "A",
      "row": 1,
      "col": 10
    }
  ],
  "expiresAt": "2026-07-26T22:30:00"
}
```

프론트 처리:

1. `orderId`, `seats`, `expiresAt` 저장
2. 점유 만료 시간을 화면에 표시
3. 배송지/쿠폰/결제수단 입력 화면으로 이동

### 10. 쿠폰 확인

보유 쿠폰:

```http
GET /api/v1/coupon/me
Authorization: Bearer {accessToken}
```

다운로드 가능한 쿠폰:

```http
GET /api/v1/coupon/downloadable
Authorization: Bearer {accessToken}
```

선택 쿠폰의 적용 가능 여부:

```http
POST /api/v1/coupon/available
Authorization: Bearer {accessToken}
Content-Type: application/json
```

체크아웃 준비 요청에서 쿠폰을 사용할 경우 `userCouponId`를 포함한다.

### 11. 체크아웃 준비

좌석 점유 이후 예약, 배송, 결제 대기 정보를 생성한다.

```http
POST /api/v1/checkout/prepare
Authorization: Bearer {accessToken}
X-Queue-Token: {queueToken}
Content-Type: application/json
```

요청:

```json
{
  "orderId": "...",
  "eventId": 1,
  "seats": [
    {
      "id": 101,
      "zone": "A",
      "row": 1,
      "col": 10
    }
  ],
  "userCouponId": 10,
  "delivery": {
    "recipientName": "홍길동",
    "recipientPhone": "010-1234-5678",
    "zipCode": "01234",
    "address": "서울시 ...",
    "detailAddress": "101동 1001호",
    "deliveryMessage": "문 앞에 놓아주세요"
  },
  "paymentMethod": "CREDIT_CARD",
  "idempotencyKey": "client-generated-unique-key",
  "depositorName": "홍길동"
}
```

응답:

```json
{
  "reservationId": 1,
  "orderId": "...",
  "paymentId": 1,
  "paymentNo": "...",
  "paymentMethod": "CREDIT_CARD",
  "paymentStatus": "READY",
  "totalTicketAmount": 120000,
  "discountAmount": 10000,
  "amount": 110000
}
```

프론트 처리:

1. `reservationId`, `paymentNo`, 결제 금액 저장
2. 선택한 결제수단에 맞는 결제 화면 표시
3. 카드 결제는 카드 승인 API 호출
4. 무통장은 가상계좌 발급 API 호출 후 입금 안내 표시

### 12. 결제 처리

#### 12-1. 카드 결제 승인

```http
POST /api/v1/payments/card/approve
Authorization: Bearer {accessToken}
X-Queue-Token: {queueToken}
Content-Type: application/json
```

요청:

```json
{
  "paymentNo": "...",
  "cardCompany": "KB",
  "cardNumber": "1234-5678-9012-3456",
  "cvc": "123",
  "cardPassword": "qwe123!"
}
```

응답:

```json
{
  "paymentId": 1,
  "reservationId": 1,
  "orderId": "...",
  "paymentNo": "...",
  "method": "CREDIT_CARD",
  "status": "PAID",
  "amount": 110000,
  "bankName": null,
  "accountNumber": null,
  "depositorName": null,
  "requestedAt": "...",
  "paidAt": "...",
  "expiresAt": null
}
```

프론트 처리:

1. 결제 성공 화면으로 이동
2. `reservationId` 기준으로 예약 상세 또는 티켓 조회
3. 실패 시 alert 표시 후 카드 재시도 또는 무통장 전환 허용

#### 12-2. 가상계좌 발급

```http
POST /api/v1/payments/virtual-account/issue
Authorization: Bearer {accessToken}
X-Queue-Token: {queueToken}
Content-Type: application/json
```

요청:

```json
{
  "paymentNo": "...",
  "bankCode": "KB",
  "depositorName": "홍길동"
}
```

응답:

```json
{
  "paymentId": 1,
  "reservationId": 1,
  "orderId": "...",
  "paymentNo": "...",
  "method": "BANK_TRANSFER",
  "status": "WAITING_DEPOSIT",
  "amount": 110000,
  "bankName": "KB국민은행",
  "accountNumber": "1111-2222-3333-4444",
  "depositorName": "홍길동",
  "requestedAt": "...",
  "paidAt": null,
  "expiresAt": "2026-07-27 23:59:59"
}
```

프론트 처리:

1. 은행명, 계좌번호, 입금자명, 입금 기한 표시
2. 사용자는 입금 완료 전까지 예매 대기 상태로 본다
3. 실제 입금 확인은 사용자 API가 아니라 은행 콜백 시뮬레이션 API로 처리한다

#### 12-3. 가상계좌 입금 시뮬레이션

```http
POST /api/v1/payments/virtual-account/deposit
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "accountNumber": "1111-2222-3333-4444",
  "amount": 110000
}
```

응답:

```json
{
  "paymentId": 1,
  "reservationId": 1,
  "orderId": "...",
  "paymentNo": "...",
  "method": "BANK_TRANSFER",
  "status": "PAID",
  "amount": 110000,
  "bankName": "KB국민은행",
  "accountNumber": "1111-2222-3333-4444",
  "depositorName": "홍길동",
  "requestedAt": "...",
  "paidAt": "...",
  "expiresAt": "2026-07-27 23:59:59"
}
```

처리 기준:

1. 계좌번호, 입금 금액, 입금 기한 검증
2. 성공 시 결제, 예약, 티켓, 좌석 상태를 완료 처리
3. 현재 단계에서는 Kafka 결제 완료 이벤트 발행은 비활성화되어 있다

### 13. 예약 조회

내 예약 목록:

```http
GET /api/v1/reservation/select?page=0&size=10
Authorization: Bearer {accessToken}
```

내 예약 상세:

```http
GET /api/v1/reservation/select/id/{reservationId}
Authorization: Bearer {accessToken}
```

예약 목록 주요 쿼리 파라미터:

| 파라미터 | 설명 |
| --- | --- |
| `eventId` | 이벤트 ID |
| `seatId` | 좌석 ID |
| `startDate`, `endDate` | 예약일 검색 범위 |
| `status` | 예약 상태 |
| `page`, `size`, `sort` | 페이징/정렬 |

### 14. 티켓 조회

예약에 포함된 내 티켓 목록:

```http
GET /api/v1/ticket/reservation/{reservationId}
Authorization: Bearer {accessToken}
```

프론트 처리:

1. 예약 상세 화면에서 티켓 목록 표시
2. 티켓 QR/바코드 기능이 추가되면 이 응답을 기준으로 화면 확장

## 대표 정상 시나리오

```text
1. POST /api/v1/login
2. GET  /api/v1/event/select
3. GET  /api/v1/event/select/id/{eventId}
4. GET  /api/v1/area/layout/event/{eventId}
5. GET  /api/v1/area/select?eventId={eventId}
6. GET  /api/v1/seat/select?eventId={eventId}&areaId={areaId}
7. POST /api/v1/queue/events/{eventId}/enter
8. GET  /api/v1/queue/events/{eventId}/status until READY
9. POST /api/v1/seat/occupy with X-Queue-Token
10. POST /api/v1/checkout/prepare with X-Queue-Token
11. POST /api/v1/payments/card/approve 또는 POST /api/v1/payments/virtual-account/issue
12. 무통장 선택 시 POST /api/v1/payments/virtual-account/deposit
13. GET  /api/v1/reservation/select/id/{reservationId}
14. GET  /api/v1/ticket/reservation/{reservationId}
```

## 아직 확정이 필요한 부분

아래 항목은 프론트 구현 전에 백엔드/프론트가 같이 확정하는 것이 좋다.

| 항목 | 현재 상태 | 결정 필요 |
| --- | --- | --- |
| 외부 Base URL | 서비스별 내부 경로만 있음 | Gateway/BFF/프론트 서버 URL 정책 |
| Queue Token 만료 UI | `expiresInSeconds`, `expiresAt` 존재 | 만료 시 재진입/좌석 해제 UX |
| 결제수단 목록 | `PaymentMethod` enum 사용 | 프론트 선택지 라벨/지원 범위 |
| 에러 응답 포맷 | 서비스별 문자열 응답 가능 | 공통 `{code,message}` 포맷 여부 |
| 좌석 실시간 갱신 | 조회/점유 API 존재 | 폴링, SSE, WebSocket 중 선택 |
| 주문 ID 생성 주체 | 프론트가 `orderId` 전달 | 생성 규칙과 중복 방지 규칙 |

### Queue Token 재진입 정책

- `GET /api/v1/queue/events/{eventId}/status`는 `X-Queue-Token`이 있을 때만 기존 READY 토큰을 복구한다.
- 브라우저/탭 종료 후 재대기를 원하면 `queueToken`은 `localStorage`가 아니라 `sessionStorage` 또는 메모리에 저장한다.