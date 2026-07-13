# 좌석 시나리오

## 목차

- [좌석 등록](#좌석-등록)
- [좌석 단건 조회](#좌석-단건-조회)
- [좌석 조건 조회](#좌석-조건-조회)
- [좌석 수정](#좌석-수정)
- [좌석 단건 삭제](#좌석-단건-삭제)
- [좌석 다건 삭제](#좌석-다건-삭제)
- [구역 기준 좌석 삭제](#구역-기준-좌석-삭제)
- [이벤트 좌석 캐시 적재](#이벤트-좌석-캐시-적재)
- [구역 좌석 캐시 적재](#구역-좌석-캐시-적재)
- [이벤트 좌석 캐시 삭제](#이벤트-좌석-캐시-삭제)
- [구역 좌석 캐시 삭제](#구역-좌석-캐시-삭제)
- [좌석 캐시 테스트 잠금](#좌석-캐시-테스트-잠금)
- [좌석 캐시 테스트 잠금 해제](#좌석-캐시-테스트-잠금-해제)
- [좌석 점유](#좌석-점유)

## 좌석 등록

관리자가 특정 이벤트 또는 구역에 좌석을 행/열 단위로 생성하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/seat/insert
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `InsertSeatRequest`입니다.

```json
{
  "eventId": 1,
  "areaId": 10,
  "insertSeatAreaConfigs": [
    {
      "zone": "A",
      "grade": "VIP",
      "rows": 10,
      "cols": 12,
      "price": 165000,
      "startRow": 1,
      "startCol": 1,
      "startX": 100.0,
      "startY": 200.0
    }
  ]
}
```

### 처리 흐름

```text
Browser
  -> SeatController.insert(...)
  -> SeatService.insert(...)
  -> SeatRepositoryImpl.insert(...)
  -> EventRepositoryImpl.selectById(...)
  -> AreaRepositoryImpl.selectById(...) optional
  -> SeatRepositoryImpl.isExist(...)
  -> SeatJpaRepository.save(...)
  -> Browser
```

### 단계별 설명

1. `SeatController.insert(...)`
   - JSON 요청 본문을 `InsertSeatRequest`로 받습니다.
   - `seatService.insert(info)`를 호출합니다.
   - 서비스 호출이 끝나면 본문 없이 `200 OK`를 반환합니다.

2. `SeatService.insert(...)`
   - 요청에 포함된 `InsertSeatAreaConfig` 정보를 로그로 남깁니다.
   - `repository.insert(info)`를 호출합니다.

3. `SeatRepositoryImpl.insert(...)`
   - `eventRepository.selectById(eventId)`로 이벤트가 존재하는지 확인합니다.
   - `areaId`가 있으면 `areaRepository.selectById(areaId)`로 구역을 조회합니다.
   - `isExist(cond)`로 해당 이벤트에 예약 불가 상태의 좌석이 이미 있는지 확인합니다.
   - 각 config의 `rows`, `cols`를 기준으로 좌석을 생성합니다.
   - `startRow`, `startCol`, `startX`, `startY`, 간격, 회전값을 이용해 좌석 번호와 위치값을 계산합니다.
   - 생성된 좌석은 `AVAILABLE` 상태로 저장됩니다.
   - 500개 단위로 `entityManager.flush()`와 `clear()`를 수행합니다.

### 응답

정상 처리되면 응답 본문 없이 `200 OK`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 이벤트가 존재하지 않음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 구역이 존재하지 않음 | `AreaRepositoryImpl.selectById(...)` | `404 Not Found` |
| 예약 불가 상태의 기존 좌석 존재 | `SeatRepositoryImpl.isExist(...)` | `400 Bad Request` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 좌석 단건 조회

브라우저에서 특정 좌석 상세 정보를 조회하는 시나리오입니다.

### 요청

```text
GET /ticket/api/v1/seat/select/id/{seatId}
권한: USER, ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.selectById(...)
  -> SeatService.selectById(...)
  -> SeatRepositoryImpl.selectById(...)
  -> SeatJpaRepository.findById(...)
  -> Seat.toDto(...)
  -> Browser
```

### 단계별 설명

1. `SeatController.selectById(...)`
   - URL path의 `seatId`를 받습니다.
   - `seatService.selectById(id)`를 호출합니다.

2. `SeatService.selectById(...)`
   - 읽기 전용 트랜잭션에서 동작합니다.
   - `repository.selectById(id)`로 좌석 엔티티를 조회합니다.
   - 조회 결과를 `toDto()`로 변환합니다.

3. `SeatRepositoryImpl.selectById(...)`
   - `jpaRepository.findById(id)`로 좌석을 찾습니다.
   - 좌석이 없으면 `SeatNotExistException`을 발생시킵니다.

### 응답

정상 처리되면 `SeatResponse`를 `200 OK`로 반환합니다.

```json
{
  "seatId": 1,
  "zone": "A",
  "seatRow": 1,
  "seatCol": 1,
  "seatName": "A 1열 1번",
  "grade": "VIP",
  "price": 165000,
  "status": "AVAILABLE",
  "eventId": 1,
  "areaId": 10,
  "areaName": "VIP"
}
```

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 좌석이 존재하지 않음 | `SeatRepositoryImpl.selectById(...)` | `404 Not Found` |
| 인증 또는 권한 없음 | Spring Security | `401` 또는 `403` |

## 좌석 조건 조회

브라우저에서 좌석 목록을 검색 조건, 페이지, 정렬 정보로 조회하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/seat/select
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `SeatCondRequest`입니다.

```json
{
  "eventId": 1,
  "areaId": 10,
  "zone": "A",
  "status": "AVAILABLE",
  "page": 0,
  "size": 20,
  "sort": ["seatRow-asc"]
}
```

### 처리 흐름

```text
Browser
  -> SeatController.selectByCond(...)
  -> SeatService.selectByCond(...)
  -> SeatService.makeSortInfo(...)
  -> SeatRepositoryImpl.selectByCond(...)
  -> Seat.toDto(...)
  -> CustomPageResponse.of(...)
  -> Browser
```

### 단계별 설명

1. `SeatController.selectByCond(...)`
   - JSON 요청 본문을 `SeatCondRequest`로 받습니다.
   - `seatService.selectByCond(cond)`를 호출합니다.

2. `SeatService.selectByCond(...)`
   - `cond.getPage()`, `cond.getSize()`, `makeSortInfo(cond.getSort())`로 `PageRequest`를 만듭니다.
   - `repository.selectByCond(cond, pageable)`로 조건 조회를 수행합니다.
   - 조회된 `Page<Seat>`를 `Page<SeatResponse>`로 변환합니다.
   - `CustomPageResponse.of(...)`로 페이지 응답을 만듭니다.

3. `SeatRepositoryImpl.selectByCond(...)`
   - Querydsl로 조건을 조립합니다.
   - `event`는 fetch join, `area`는 left fetch join으로 함께 조회합니다.
   - `seatId`, `eventId`, `areaId`, `zone`, `seatRow`, `seatCol`, `grade`, `status` 조건을 반영합니다.
   - 목록 조회와 count 조회를 수행합니다.

### 응답

정상 처리되면 `CustomPageResponse<SeatResponse>`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 조건의 `eventId`가 존재하지 않음 | `SeatRepositoryImpl.eventIdEq(...)` | `404 Not Found` |
| 조건의 `areaId`가 존재하지 않음 | `SeatRepositoryImpl.areaIdEq(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 좌석 수정

관리자가 여러 좌석의 가격, 상태, 배치 위치값을 수정하는 시나리오입니다.

### 요청

```text
PUT /ticket/api/v1/seat/update
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `UpdateSeatRequest`입니다.

```json
{
  "updateSeatAreaConfigs": [
    {
      "id": 1,
      "price": 175000,
      "status": "AVAILABLE",
      "positionX": 120.0,
      "positionY": 220.0
    }
  ]
}
```

### 처리 흐름

```text
Browser
  -> SeatController.update(...)
  -> SeatService.update(...)
  -> SeatRepositoryImpl.update(...)
  -> SeatRepositoryImpl.selectById(...)
  -> Seat.update(...)
  -> Browser
```

### 단계별 설명

1. `SeatController.update(...)`
   - JSON 요청 본문을 `UpdateSeatRequest`로 받습니다.
   - `seatService.update(info)`를 호출합니다.
   - 서비스 호출이 끝나면 본문 없이 `200 OK`를 반환합니다.

2. `SeatService.update(...)`
   - `repository.update(info)`를 호출합니다.

3. `SeatRepositoryImpl.update(...)`
   - `updateSeatAreaConfigs`를 순회합니다.
   - 각 config의 `id`로 좌석을 조회합니다.
   - `seat.update(config)`로 요청값이 있는 필드만 변경합니다.
   - 트랜잭션 커밋 시 변경 감지로 DB에 반영됩니다.

### 응답

정상 처리되면 응답 본문 없이 `200 OK`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 수정 대상 좌석 없음 | `SeatRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 좌석 단건 삭제

관리자가 특정 좌석 하나를 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/seat/delete/id/{seatId}
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.delete(...)
  -> SeatService.delete(...)
  -> SeatRepositoryImpl.delete(...)
  -> SeatRepositoryImpl.selectById(...)
  -> SeatJpaRepository.delete(...)
  -> Browser
```

### 단계별 설명

1. `SeatController.delete(...)`
   - URL path의 `seatId`를 받습니다.
   - `seatService.delete(seatId)`를 호출합니다.

2. `SeatService.delete(...)`
   - `repository.delete(id)`를 호출합니다.

3. `SeatRepositoryImpl.delete(...)`
   - `selectById(id)`로 삭제할 좌석을 조회합니다.
   - `jpaRepository.delete(seat)`로 삭제합니다.

### 응답

정상 처리되면 응답 본문 없이 `200 OK`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 삭제 대상 좌석 없음 | `SeatRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 좌석 다건 삭제

관리자가 여러 좌석을 ID 목록으로 삭제하는 시나리오입니다. `/delete`와 `/delete/bulk`는 같은 서비스 메서드를 사용합니다.

### 요청

```text
DELETE /ticket/api/v1/seat/delete
DELETE /ticket/api/v1/seat/delete/bulk
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `DeleteSeatRequest`입니다.

```json
{
  "seatIdList": [1, 2, 3]
}
```

### 처리 흐름

```text
Browser
  -> SeatController.deleteBySeatIdList(...) 또는 SeatController.deleteBulk(...)
  -> SeatService.deleteBySeatIdList(...)
  -> SeatRepositoryImpl.deleteByIdList(...)
  -> SeatJpaRepository.deleteBySeatIdIn(...)
  -> Browser
```

### 단계별 설명

1. `SeatController.deleteBySeatIdList(...)`, `SeatController.deleteBulk(...)`
   - JSON 요청 본문을 `DeleteSeatRequest`로 받습니다.
   - `seatService.deleteBySeatIdList(info)`를 호출합니다.
   - 서비스 호출이 끝나면 본문 없이 `200 OK`를 반환합니다.

2. `SeatService.deleteBySeatIdList(...)`
   - `seatIdList`가 비어 있지 않으면 `repository.deleteByIdList(...)`를 호출합니다.
   - 현재 구현은 빈 목록이면 별도 예외 없이 아무 작업도 하지 않습니다.

3. `SeatRepositoryImpl.deleteByIdList(...)`
   - `jpaRepository.deleteBySeatIdIn(seatIdList)`로 좌석들을 삭제합니다.

### 응답

정상 처리되면 응답 본문 없이 `200 OK`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 기준 좌석 삭제

관리자가 특정 구역에 속한 좌석을 모두 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/seat/delete/area/{areaId}
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.deleteByAreaId(...)
  -> SeatService.deleteByAreaId(...)
  -> SeatRepositoryImpl.deleteByAreaId(...)
  -> SeatJpaRepository.deleteByAreaAreaId(...)
  -> Browser
```

### 단계별 설명

1. `SeatController.deleteByAreaId(...)`
   - URL path의 `areaId`를 받습니다.
   - `seatService.deleteByAreaId(areaId)`를 호출합니다.

2. `SeatService.deleteByAreaId(...)`
   - `repository.deleteByAreaId(areaId)`를 호출합니다.

3. `SeatRepositoryImpl.deleteByAreaId(...)`
   - `jpaRepository.deleteByAreaAreaId(areaId)`로 해당 구역의 좌석을 삭제합니다.

### 응답

정상 처리되면 응답 본문 없이 `200 OK`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 좌석 캐시 적재

운영자가 특정 이벤트의 좌석 상태를 Redis에 적재하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/seat/cache/warm-up/event/{eventId}?mode=MISSING_ONLY
권한: ADMIN
```

`mode`는 `MISSING_ONLY`, `OVERWRITE`를 사용할 수 있습니다.

### 처리 흐름

```text
Browser
  -> SeatController.warmUpEventSeats(...)
  -> SeatService.warmUpEventSeatsToCache(...)
  -> SeatCacheService.warmUpEventSeatsToCache(...)
  -> SeatRepositoryImpl.selectByEventId(...)
  -> SeatCacheService.warmUpSeatCache(...)
  -> Redis
  -> Browser
```

### 단계별 설명

1. `SeatController.warmUpEventSeats(...)`
   - URL path의 `eventId`와 query parameter의 `mode`를 받습니다.
   - `seatService.warmUpEventSeatsToCache(eventId, mode)`를 호출합니다.

2. `SeatCacheService.warmUpEventSeatsToCache(...)`
   - `repository.selectByEventId(eventId)`로 이벤트 좌석 목록을 조회합니다.
   - `warmUpSeatCache(seats, mode)`로 Redis에 좌석 상태를 저장합니다.

3. `SeatCacheService.warmUpSeatCache(...)`
   - `OVERWRITE`이면 Redis key 값을 `AVAILABLE`로 덮어씁니다.
   - `MISSING_ONLY`이면 없는 key만 `AVAILABLE`로 추가합니다.
   - 좌석 캐시 TTL은 기본 7일입니다.

### 응답

정상 처리되면 처리 결과 문자열을 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 이벤트에 좌석이 없음 | `SeatRepositoryImpl.selectByEventId(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 좌석 캐시 적재

운영자가 특정 구역의 좌석 상태를 Redis에 적재하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/seat/cache/warm-up/area/{areaId}?mode=MISSING_ONLY
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.warmUpAreaSeats(...)
  -> SeatService.warmUpAreaSeatsToCache(...)
  -> SeatCacheService.warmUpAreaSeatsToCache(...)
  -> SeatRepositoryImpl.selectByAreaId(...)
  -> SeatCacheService.warmUpSeatCache(...)
  -> Redis
  -> Browser
```

### 단계별 설명

1. `SeatController.warmUpAreaSeats(...)`
   - URL path의 `areaId`와 query parameter의 `mode`를 받습니다.
   - `seatService.warmUpAreaSeatsToCache(areaId, mode)`를 호출합니다.

2. `SeatCacheService.warmUpAreaSeatsToCache(...)`
   - `repository.selectByAreaId(areaId)`로 구역 좌석 목록을 조회합니다.
   - 조회된 좌석을 `warmUpSeatCache(...)`로 Redis에 저장합니다.

### 응답

정상 처리되면 처리 결과 문자열을 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 구역에 좌석이 없음 | `SeatRepositoryImpl.selectByAreaId(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 좌석 캐시 삭제

운영자가 특정 이벤트의 좌석 캐시와 잠금 key를 Redis에서 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/seat/cache/event/{eventId}
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.deleteEventSeatCache(...)
  -> SeatService.deleteEventSeatsFromCache(...)
  -> SeatCacheService.deleteEventSeatsFromCache(...)
  -> SeatRepositoryImpl.selectByEventId(...)
  -> SeatCacheService.deleteSeatCache(...)
  -> Redis
  -> Browser
```

### 단계별 설명

1. `SeatController.deleteEventSeatCache(...)`
   - URL path의 `eventId`를 받습니다.
   - `seatService.deleteEventSeatsFromCache(eventId)`를 호출합니다.

2. `SeatCacheService.deleteEventSeatsFromCache(...)`
   - 이벤트 좌석 목록을 조회합니다.
   - 각 좌석의 Redis 상태 key와 `:lock` key를 삭제합니다.

### 응답

정상 처리되면 처리 결과 문자열을 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 이벤트에 좌석이 없음 | `SeatRepositoryImpl.selectByEventId(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 좌석 캐시 삭제

운영자가 특정 구역의 좌석 캐시와 잠금 key를 Redis에서 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/seat/cache/area/{areaId}
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.deleteAreaSeatCache(...)
  -> SeatService.deleteAreaSeatsFromCache(...)
  -> SeatCacheService.deleteAreaSeatsFromCache(...)
  -> SeatRepositoryImpl.selectByAreaId(...)
  -> SeatCacheService.deleteSeatCache(...)
  -> Redis
  -> Browser
```

### 단계별 설명

1. `SeatController.deleteAreaSeatCache(...)`
   - URL path의 `areaId`를 받습니다.
   - `seatService.deleteAreaSeatsFromCache(areaId)`를 호출합니다.

2. `SeatCacheService.deleteAreaSeatsFromCache(...)`
   - 구역 좌석 목록을 조회합니다.
   - 각 좌석의 Redis 상태 key와 `:lock` key를 삭제합니다.

### 응답

정상 처리되면 처리 결과 문자열을 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 구역에 좌석이 없음 | `SeatRepositoryImpl.selectByAreaId(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 좌석 캐시 테스트 잠금

현재 인증 사용자를 기준으로 특정 좌석을 Redis에서 테스트 잠금 처리하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/seat/cache/seat/{seatId}/test-lock
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.lockSeatCacheForCurrentUser(...)
  -> SeatService.lockSeatCacheForUser(...)
  -> SeatCacheService.lockSeatCacheForUser(...)
  -> SeatRepositoryImpl.selectById(...)
  -> Redis setIfAbsent(lockKey)
  -> Redis set(seatKey)
  -> Browser
```

### 단계별 설명

1. `SeatController.lockSeatCacheForCurrentUser(...)`
   - URL path의 `seatId`를 받습니다.
   - `@AuthenticationPrincipal`에서 현재 사용자 ID를 받습니다.
   - `seatService.lockSeatCacheForUser(seatId, currentUserId)`를 호출합니다.

2. `SeatCacheService.lockSeatCacheForUser(...)`
   - 좌석을 조회하고 Redis key를 만듭니다.
   - DB 상태 또는 Redis 상태가 이미 사용 불가이면 예외를 발생시킵니다.
   - `setIfAbsent(lockKey, value, 10분)`으로 잠금 key를 생성합니다.
   - 좌석 상태 key에도 `LOCKED:{userId}:{orderId}` 값을 10분 TTL로 저장합니다.

### 응답

정상 처리되면 처리 결과 문자열을 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 좌석이 존재하지 않음 | `SeatRepositoryImpl.selectById(...)` | `404 Not Found` |
| 이미 점유 또는 예약된 좌석 | `SeatCacheService.lockSeatCacheForUser(...)` | `409 Conflict` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 좌석 캐시 테스트 잠금 해제

Redis에서 테스트 잠금된 좌석을 다시 예매 가능 상태로 되돌리는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/seat/cache/seat/{seatId}/test-unlock
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> SeatController.unlockSeatCache(...)
  -> SeatService.unlockSeatCache(...)
  -> SeatCacheService.unlockSeatCache(...)
  -> SeatRepositoryImpl.selectById(...)
  -> Redis set(AVAILABLE)
  -> Redis delete(lockKey)
  -> Browser
```

### 단계별 설명

1. `SeatController.unlockSeatCache(...)`
   - URL path의 `seatId`를 받습니다.
   - `seatService.unlockSeatCache(seatId)`를 호출합니다.

2. `SeatCacheService.unlockSeatCache(...)`
   - 좌석을 조회하고 Redis key를 만듭니다.
   - 현재 Redis 값이 없으면 `SeatCacheNotFoundException`을 발생시킵니다.
   - 현재 Redis 값이 `LOCKED:`로 시작하지 않으면 `SeatAlreadyOccupiedException`을 발생시킵니다.
   - 좌석 상태 key를 `AVAILABLE`로 바꾸고 `:lock` key를 삭제합니다.

### 응답

정상 처리되면 처리 결과 문자열을 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 좌석 캐시가 없음 | `SeatCacheService.unlockSeatCache(...)` | `404 Not Found` |
| 잠금 상태가 아님 | `SeatCacheService.unlockSeatCache(...)` | `409 Conflict` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 좌석 점유

사용자가 예매 전에 선택한 좌석을 Redis에서 일정 시간 동안 임시 점유하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/seat/occupy
Content-Type: application/json
권한: USER, ADMIN
```

요청 본문은 `SeatOccupyRequest`입니다.

```json
{
  "eventId": 1,
  "userId": "100",
  "maxTicketsPerPerson": 4,
  "seats": [
    {
      "id": 1,
      "zone": "A",
      "row": 1,
      "col": 1
    }
  ]
}
```

### 처리 흐름

```text
Browser
  -> SeatController.occupySeat(...)
  -> SeatService.occupySeat(...)
  -> SeatCacheService.occupySeat(...)
  -> SeatCacheService.validateUserPurchaseLimit(...)
  -> Redis get(seatKey)
  -> SeatCacheService.validateSeatAvailableFromDatabase(...) optional
  -> Redis setIfAbsent(lockKey)
  -> Redis set(seatKey)
  -> SeatOccupyResponse
  -> Browser
```

### 단계별 설명

1. `SeatController.occupySeat(...)`
   - JSON 요청 본문을 `SeatOccupyRequest`로 받습니다.
   - `seatService.occupySeat(request)`를 호출합니다.

2. `SeatCacheService.occupySeat(...)`
   - `validateUserPurchaseLimit(request)`로 사용자별 예매 가능 매수 초과 여부를 확인합니다.
   - 주문 ID를 생성하고 만료 시간을 현재 시각 + 10분으로 계산합니다.
   - 요청 좌석들을 먼저 순회하면서 Redis 상태가 모두 `AVAILABLE`인지 확인합니다.
   - Redis에 좌석 상태 key가 없으면 DB에서 좌석이 해당 이벤트에 속하고 `AVAILABLE`인지 확인합니다.
   - 모든 좌석이 가능하면 각 좌석의 `:lock` key를 `setIfAbsent`로 생성합니다.
   - 잠금이 성공하면 좌석 상태 key에 `LOCKED:{userId}:{orderId}` 값을 10분 TTL로 저장합니다.
   - 중간에 실패하면 이미 획득한 lock key와 갱신한 seat key를 롤백합니다.

3. `SeatOccupyResponse`
   - 이후 예약 생성에서 사용할 `orderId`를 포함합니다.
   - `expiresAt`은 임시 점유 만료 시각입니다.

### 응답

정상 처리되면 `SeatOccupyResponse`를 `200 OK`로 반환합니다.

```json
{
  "orderId": "ORD-20260713053000-abcd1234efgh",
  "eventId": 1,
  "userId": "100",
  "seats": [
    {
      "id": 1,
      "zone": "A",
      "row": 1,
      "col": 1
    }
  ],
  "expiresAt": "2026-07-13T05:40:00"
}
```

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 사용자별 예매 가능 매수 초과 | `SeatCacheService.validateUserPurchaseLimit(...)` | `500 Internal Server Error` |
| 이미 점유 또는 예약된 좌석 포함 | `SeatCacheService.occupySeat(...)` | `409 Conflict` |
| 좌석이 이벤트와 일치하지 않음 | `SeatCacheService.validateSeatAvailableFromDatabase(...)` | `500 Internal Server Error` |
| 좌석 점유 중 예상치 못한 오류 | `SeatCacheService.occupySeat(...)` | `500 Internal Server Error` |
| 인증 또는 권한 없음 | Spring Security | `401` 또는 `403` |
