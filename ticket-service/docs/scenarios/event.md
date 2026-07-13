# 이벤트 시나리오

## 목차

- [이벤트 등록](#이벤트-등록)
- [이벤트 단건 조회](#이벤트-단건-조회)
- [이벤트 조건 조회](#이벤트-조건-조회)
- [이벤트 수정](#이벤트-수정)
- [이벤트 수정과 포스터 변경](#이벤트-수정과-포스터-변경)
- [이벤트 단건 삭제](#이벤트-단건-삭제)
- [이벤트 다건 삭제](#이벤트-다건-삭제)

## 이벤트 등록

관리자가 브라우저에서 이벤트 등록 폼을 작성하고 저장 버튼을 누르는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/event/insert
Content-Type: multipart/form-data
권한: ADMIN
```

요청은 두 개의 파트로 들어옵니다.

| part | 설명 |
| --- | --- |
| `event` | `InsertEventRequest` JSON 문자열 |
| `posterImage` | 포스터 이미지 파일, 선택값 |

### 처리 흐름

```text
Browser
  -> EventController.insert(...)
  -> EventController.readEvent(...)
  -> EventService.insert(...)
  -> EventRepositoryImpl.insert(...)
  -> EventRepositoryImpl.isExist(...)
  -> EventJpaRepository.save(...)
  -> FileStorageService.saveEventPoster(...)
  -> Event.updatePosterUrl(...)
  -> Event.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `EventController.insert(...)`
   - `multipart/form-data` 요청을 받습니다.
   - `event` 파트는 문자열로 받고, `posterImage` 파트는 `MultipartFile`로 받습니다.
   - `readEvent(event, InsertEventRequest.class)`를 호출해 JSON 문자열을 `InsertEventRequest`로 변환합니다.
   - 변환된 요청 DTO와 포스터 파일을 `eventService.insert(...)`로 넘깁니다.

2. `EventService.insert(...)`
   - `repository.insert(info)`를 호출해 이벤트를 먼저 DB에 저장합니다.
   - 저장 후 생성된 `eventId`를 사용해 `fileStorageService.saveEventPoster(event.getEventId(), posterImage)`를 호출합니다.
   - 포스터 저장 결과로 받은 URL을 `event.updatePosterUrl(posterUrl)`로 이벤트에 반영합니다.
   - 최종적으로 `event.toResponse()`를 호출해 응답 DTO를 만듭니다.

3. `EventRepositoryImpl.insert(...)`
   - `artistName`, `title`, `venue`, `eventDate`, `status` 조건으로 중복 이벤트가 있는지 확인합니다.
   - 중복 확인은 `isExist(cond)`에서 Querydsl로 수행합니다.
   - 중복이 없으면 `new Event(info)`로 이벤트 엔티티를 만들고 `jpaRepository.save(...)`로 저장합니다.

4. `Event` 생성자
   - 요청값을 이벤트 엔티티 필드에 복사합니다.
   - `availableSeats`는 `totalSeats`와 같은 값으로 시작합니다.
   - `status`는 `ON_SALE`로 시작합니다.

5. `FileStorageService.saveEventPoster(...)`
   - 포스터 파일이 없으면 `null`을 반환합니다.
   - 파일이 있으면 이미지 확장자와 Content-Type을 검증한 뒤 `uploads/events/posters/{eventId}` 아래에 저장합니다.
   - 저장된 파일의 공개 URL을 반환합니다.

### 응답

정상 처리되면 `EventController.insert(...)`는 `ResponseEntity.ok(...)`로 `EventResponse`를 반환합니다.

```json
{
  "eventId": 1,
  "artistName": "아이유",
  "title": "아이유 콘서트",
  "venue": "올림픽 체조경기장",
  "posterUrl": "/ticket/uploads/events/posters/1/{fileName}.png",
  "eventDateTime": "2026년 9월 18일 18시 00분",
  "totalSeats": 14500,
  "availableSeats": 14500,
  "status": "ON_SALE",
  "maxTicketsPerPerson": 4
}
```

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| `event` JSON 파싱 실패 | `EventController.readEvent(...)` | `400 Bad Request` |
| 중복 이벤트 존재 | `EventRepositoryImpl.isExist(...)` | `400 Bad Request` |
| 포스터 파일 형식 오류 | `FileStorageService.saveEventPoster(...)` | `400 Bad Request` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 단건 조회

브라우저에서 특정 이벤트 상세 정보를 조회하는 시나리오입니다.

### 요청

```text
GET /ticket/api/v1/event/select/id/{eventId}
권한: USER, ADMIN
```

### 처리 흐름

```text
Browser
  -> EventController.selectById(...)
  -> EventService.selectById(...)
  -> EventRepositoryImpl.selectById(...)
  -> EventJpaRepository.findById(...)
  -> Event.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `EventController.selectById(...)`
   - URL path의 `eventId`를 `@PathVariable`로 받습니다.
   - `eventService.selectById(eventId)`를 호출합니다.

2. `EventService.selectById(...)`
   - 읽기 전용 트랜잭션에서 동작합니다.
   - `repository.selectById(id)`로 이벤트 엔티티를 조회합니다.
   - 조회된 엔티티를 `toResponse()`로 변환합니다.

3. `EventRepositoryImpl.selectById(...)`
   - `jpaRepository.findById(id)`로 DB에서 이벤트를 찾습니다.
   - 이벤트가 없으면 `EventNotExistException`을 발생시킵니다.

### 응답

정상 처리되면 `EventResponse`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 이벤트가 존재하지 않음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 인증 또는 권한 없음 | Spring Security | `401` 또는 `403` |

## 이벤트 조건 조회

브라우저에서 이벤트 목록을 검색 조건, 페이지, 정렬 정보로 조회하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/event/select
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `EventCondRequest`입니다.

```json
{
  "artistName": "아이유",
  "venue": "올림픽",
  "page": 0,
  "size": 10,
  "sort": ["eventDateTime-desc"]
}
```

### 처리 흐름

```text
Browser
  -> EventController.selectByCond(...)
  -> EventService.selectByCond(...)
  -> EventService.makeSortInfo(...)
  -> EventRepositoryImpl.selectByCond(...)
  -> EventRepositoryImpl.searchConditions(...)
  -> Event.toResponse(...)
  -> CustomPageResponse.of(...)
  -> Browser
```

### 단계별 설명

1. `EventController.selectByCond(...)`
   - JSON 요청 본문을 `EventCondRequest`로 받습니다.
   - `eventService.selectByCond(cond)`를 호출합니다.

2. `EventService.selectByCond(...)`
   - `cond.getPage()`, `cond.getSize()`, `makeSortInfo(cond.getSort())`로 `PageRequest`를 만듭니다.
   - `repository.selectByCond(cond, pageRequest)`로 조건 조회를 수행합니다.
   - 조회된 `Page<Event>`를 `Page<EventResponse>`로 변환합니다.
   - `CustomPageResponse.of(...)`로 페이지 응답을 만듭니다.

3. `EventRepositoryImpl.selectByCond(...)`
   - Querydsl로 검색 조건을 조립합니다.
   - `searchConditions(cond)`에서 조건 값이 있는 필드만 where 조건에 반영합니다.
   - `offset`, `limit`, `orderBy`를 적용해 목록을 조회합니다.
   - 같은 조건으로 count 쿼리를 실행해 전체 개수를 구합니다.

### 응답

정상 처리되면 `CustomPageResponse<EventResponse>`를 `200 OK`로 반환합니다.

```json
{
  "content": [
    {
      "eventId": 1,
      "artistName": "아이유",
      "title": "아이유 콘서트",
      "status": "ON_SALE"
    }
  ],
  "size": 10,
  "number": 0,
  "totalElements": 1,
  "totalPages": 1
}
```

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 잘못된 정렬 방향 값 | `EventService.makeSortInfo(...)` | `400 Bad Request` 또는 서버 예외 |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 수정

관리자가 이벤트 정보를 수정하는 시나리오입니다. 포스터를 변경하지 않는 경우 JSON 요청을 사용합니다.

### 요청

```text
PUT /ticket/api/v1/event/update/id/{eventId}
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `UpdateEventRequest`입니다.

```json
{
  "title": "수정된 콘서트명",
  "venue": "수정된 공연장",
  "totalSeats": 15000,
  "status": "ON_SALE"
}
```

### 처리 흐름

```text
Browser
  -> EventController.update(...)
  -> EventService.update(...)
  -> EventRepositoryImpl.update(...)
  -> EventRepositoryImpl.selectById(...)
  -> Event.update(...)
  -> Event.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `EventController.update(...)`
   - URL path의 `eventId`를 받습니다.
   - JSON 요청 본문을 `UpdateEventRequest`로 받습니다.
   - `eventService.update(eventId, info)`를 호출합니다.

2. `EventService.update(...)`
   - `repository.update(id, info)`를 호출합니다.
   - 반환된 이벤트 엔티티를 `toResponse()`로 변환합니다.

3. `EventRepositoryImpl.update(...)`
   - `selectById(id)`로 수정할 이벤트를 조회합니다.
   - `event.update(info)`를 호출해 요청값이 있는 필드만 변경합니다.
   - 트랜잭션 커밋 시 변경 감지로 DB에 반영됩니다.

### 응답

정상 처리되면 수정된 `EventResponse`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 수정 대상 이벤트 없음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 수정과 포스터 변경

관리자가 이벤트 정보와 포스터 이미지를 함께 수정하는 시나리오입니다.

### 요청

```text
PUT /ticket/api/v1/event/update/id/{eventId}
Content-Type: multipart/form-data
권한: ADMIN
```

요청은 두 개의 파트로 들어옵니다.

| part | 설명 |
| --- | --- |
| `event` | `UpdateEventRequest` JSON 문자열 |
| `posterImage` | 새 포스터 이미지 파일, 선택값 |

### 처리 흐름

```text
Browser
  -> EventController.update(...)
  -> EventController.readEvent(...)
  -> EventService.update(...)
  -> EventRepositoryImpl.selectById(...)
  -> FileStorageService.saveEventPoster(...)
  -> Event.update(...)
  -> FileStorageService.deleteByPublicUrl(...)
  -> Event.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `EventController.update(...)`
   - `multipart/form-data` 요청을 받습니다.
   - `event` 파트를 `readEvent(event, UpdateEventRequest.class)`로 변환합니다.
   - 변환된 요청 DTO와 포스터 파일을 `eventService.update(eventId, info, posterImage)`로 넘깁니다.

2. `EventService.update(...)`
   - `repository.selectById(id)`로 기존 이벤트를 조회합니다.
   - 기존 `posterUrl`을 `previousPosterUrl`에 보관합니다.
   - `fileStorageService.saveEventPoster(id, posterImage)`로 새 포스터를 저장합니다.
   - 새 포스터 URL이 있으면 `info.setPosterUrl(newPosterUrl)`로 수정 요청에 반영합니다.
   - `event.update(info)`로 이벤트 정보를 수정합니다.
   - 새 포스터가 저장된 경우 `fileStorageService.deleteByPublicUrl(previousPosterUrl)`로 기존 포스터 파일을 삭제합니다.

### 응답

정상 처리되면 수정된 `EventResponse`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| `event` JSON 파싱 실패 | `EventController.readEvent(...)` | `400 Bad Request` |
| 수정 대상 이벤트 없음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 포스터 파일 형식 오류 | `FileStorageService.saveEventPoster(...)` | `400 Bad Request` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 단건 삭제

관리자가 특정 이벤트 하나를 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/event/delete/id/{eventId}
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> EventController.delete(...)
  -> EventService.delete(...)
  -> EventRepositoryImpl.delete(...)
  -> EventRepositoryImpl.selectById(...)
  -> EventJpaRepository.delete(...)
  -> Event.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `EventController.delete(...)`
   - URL path의 `eventId`를 받습니다.
   - `eventService.delete(eventId)`를 호출합니다.

2. `EventService.delete(...)`
   - `repository.delete(id)`를 호출합니다.
   - 삭제된 이벤트 엔티티를 `toResponse()`로 변환합니다.

3. `EventRepositoryImpl.delete(...)`
   - `selectById(id)`로 삭제할 이벤트를 조회합니다.
   - `jpaRepository.delete(event)`로 이벤트를 삭제합니다.
   - 삭제한 이벤트 엔티티를 서비스로 반환합니다.

### 응답

정상 처리되면 삭제된 이벤트 정보를 `EventResponse`로 `200 OK` 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 삭제 대상 이벤트 없음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 다건 삭제

관리자가 여러 이벤트를 한 번에 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/event/delete/bulk
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `DeleteEventBulkRequest`입니다.

```json
{
  "eventIds": [1, 2, 3]
}
```

### 처리 흐름

```text
Browser
  -> EventController.deleteBulk(...)
  -> EventService.deleteBulk(...)
  -> EventService.delete(...)
  -> EventRepositoryImpl.delete(...)
  -> EventJpaRepository.delete(...)
  -> Browser
```

### 단계별 설명

1. `EventController.deleteBulk(...)`
   - JSON 요청 본문을 `DeleteEventBulkRequest`로 받습니다.
   - `eventService.deleteBulk(info)`를 호출합니다.
   - 서비스 호출이 끝나면 `ResponseEntity.ok().build()`로 본문 없는 응답을 반환합니다.

2. `EventService.deleteBulk(...)`
   - `eventIds`가 비어 있으면 `IllegalArgumentException`을 발생시킵니다.
   - `eventIds`를 순회하면서 각 ID마다 `delete(id)`를 호출합니다.

3. `EventService.delete(...)`
   - 단건 삭제와 같은 흐름으로 이벤트를 조회한 뒤 삭제합니다.

### 응답

정상 처리되면 응답 본문 없이 `200 OK`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| `eventIds`가 비어 있음 | `EventService.deleteBulk(...)` | `400 Bad Request` |
| 삭제 대상 이벤트 없음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |
