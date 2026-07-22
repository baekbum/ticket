# 구역 시나리오

## 목차

- [구역 단건 등록](#구역-단건-등록)
- [구역 다건 등록](#구역-다건-등록)
- [구역 SVG 등록](#구역-svg-등록)
- [이벤트 배치 조회](#이벤트-배치-조회)
- [구역 단건 조회](#구역-단건-조회)
- [구역 조건 조회](#구역-조건-조회)
- [구역 수정](#구역-수정)
- [구역 단건 삭제](#구역-단건-삭제)
- [구역 다건 삭제](#구역-다건-삭제)

## 구역 단건 등록

관리자가 특정 이벤트에 하나의 구역을 직접 등록하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/area/insert
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `InsertAreaRequest`입니다.

```json
{
  "eventId": 1,
  "areaName": "VIP",
  "grade": "VIP",
  "price": 165000,
  "status": "ACTIVE"
}
```

### 처리 흐름

```text
Browser
  -> AreaController.insert(...)
  -> AreaService.insert(...)
  -> AreaRepositoryImpl.insert(...)
  -> EventRepositoryImpl.selectById(...)
  -> AreaRepositoryImpl.isExist(...)
  -> AreaJpaRepository.save(...)
  -> Area.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.insert(...)`
   - JSON 요청 본문을 `InsertAreaRequest`로 받습니다.
   - `areaService.insert(info)`를 호출합니다.

2. `AreaService.insert(...)`
   - `repository.insert(info)`를 호출합니다.
   - 저장된 `Area` 엔티티를 `toResponse()`로 변환합니다.

3. `AreaRepositoryImpl.insert(...)`
   - `eventRepository.selectById(info.getEventId())`로 이벤트가 존재하는지 확인합니다.
   - 같은 이벤트에 같은 `areaName`이 있는지 `isExist(cond)`로 확인합니다.
   - 중복이 없으면 `new Area(info, event)`로 구역 엔티티를 만들고 저장합니다.

4. `Area` 생성자
   - 요청값을 구역 엔티티 필드에 복사합니다.
   - `status`가 없으면 `ACTIVE`로 시작합니다.

### 응답

정상 처리되면 `AreaResponse`를 `200 OK`로 반환합니다.

```json
{
  "areaId": 1,
  "eventId": 1,
  "eventTitle": "아이유 콘서트",
  "areaName": "VIP",
  "grade": "VIP",
  "price": 165000,
  "status": "ACTIVE"
}
```

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 이벤트가 존재하지 않음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 같은 이벤트에 동일 구역명 존재 | `AreaRepositoryImpl.isExist(...)` | `400 Bad Request` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 다건 등록

관리자가 여러 구역을 한 번에 등록하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/area/insert/bulk
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `InsertAreaBulkRequest`입니다.

```json
{
  "areas": [
    {
      "eventId": 1,
      "areaName": "VIP",
      "grade": "VIP",
      "price": 165000
    },
    {
      "eventId": 1,
      "areaName": "R",
      "grade": "R",
      "price": 143000
    }
  ]
}
```

### 처리 흐름

```text
Browser
  -> AreaController.insertBulk(...)
  -> AreaService.insertBulk(...)
  -> AreaRepositoryImpl.insert(...)
  -> Area.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.insertBulk(...)`
   - JSON 요청 본문을 `InsertAreaBulkRequest`로 받습니다.
   - `areaService.insertBulk(info)`를 호출합니다.

2. `AreaService.insertBulk(...)`
   - `areas`가 비어 있으면 `IllegalArgumentException`을 발생시킵니다.
   - `areas`를 순회하면서 각 요청마다 `repository.insert(...)`를 호출합니다.
   - 저장된 구역들을 `AreaResponse` 목록으로 변환합니다.

3. `AreaRepositoryImpl.insert(...)`
   - 단건 등록과 같은 방식으로 이벤트 존재 여부와 구역명 중복을 확인한 뒤 저장합니다.

### 응답

정상 처리되면 `List<AreaResponse>`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| `areas`가 비어 있음 | `AreaService.insertBulk(...)` | `400 Bad Request` |
| 이벤트가 존재하지 않음 | `EventRepositoryImpl.selectById(...)` | `404 Not Found` |
| 중복 구역 존재 | `AreaRepositoryImpl.isExist(...)` | `400 Bad Request` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 SVG 등록

관리자가 좌석 배치 SVG 파일을 업로드해서 이벤트 구역과 배치 원본을 함께 등록하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/area/insert/svg?force=false
Content-Type: multipart/form-data
권한: ADMIN
```

요청 파트와 파라미터는 다음과 같습니다.

| 이름 | 위치 | 설명 |
| --- | --- | --- |
| `eventId` | request part | 이벤트 ID 문자열 |
| `svgFile` | request part | 업로드할 SVG 파일 |
| `force` | query param | 기존 배치가 있을 때 교체할지 여부, 기본값 `false` |

### 처리 흐름

```text
Browser
  -> AreaController.insertSvg(...)
  -> AreaService.insertSvg(...)
  -> AreaService.hasAreaLayout(...)
  -> AreaService.deleteAreaLayout(...) optional
  -> AreaService.normalizeSvgFile(...)
  -> AreaService.saveEventLayout(...)
  -> AreaService.parseSvgAreas(...)
  -> AreaRepositoryImpl.insert(...)
  -> Area.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.insertSvg(...)`
   - `eventId` 파트를 문자열로 받고 `Long.parseLong(eventId)`로 변환합니다.
   - `svgFile` 파트를 `MultipartFile`로 받습니다.
   - `force` query parameter를 받아 `areaService.insertSvg(...)`를 호출합니다.

2. `AreaService.insertSvg(...)`
   - `eventId`와 `svgFile`이 유효한지 확인합니다.
   - `hasAreaLayout(eventId)`로 기존 배치 또는 구역이 있는지 확인합니다.
   - 기존 배치가 있고 `force=false`이면 `AreaLayoutAlreadyExistsException`을 발생시킵니다.
   - 기존 배치가 있고 `force=true`이면 `deleteAreaLayout(eventId)`로 기존 좌석, 구역, 레이아웃을 삭제합니다.

3. `AreaService.normalizeSvgFile(...)`
   - 업로드한 파일을 UTF-8 문자열로 읽습니다.
   - data URI 형식이면 실제 SVG 문자열로 디코딩합니다.
   - `<svg` 루트 태그부터 사용할 수 있도록 문자열을 정리합니다.

4. `AreaService.saveEventLayout(...)`
   - `eventRepository.selectById(eventId)`로 이벤트를 조회합니다.
   - 기존 `EventLayout`이 있으면 재사용하고, 없으면 새로 만듭니다.
   - `layout.replace(originalFileName, svgText)`로 파일명과 SVG 원문을 반영합니다.
   - `layoutJpaRepository.save(layout)`로 저장합니다.

5. `AreaService.parseSvgAreas(...)`
   - SVG DOM에서 `path`, `rect` 요소를 찾습니다.
   - class에 `area`가 있는 요소만 구역 후보로 사용합니다.
   - class에 `console`이 있거나 구역명이 `CONSOLE`이면 제외합니다.
   - `data-area-name`, `id`, `data-grade`, `data-price`, class 값을 이용해 `InsertAreaRequest` 목록을 만듭니다.

6. `AreaRepositoryImpl.insert(...)`
   - 파싱된 구역들을 하나씩 저장합니다.
   - 중복 구역은 `AreaDuplicateException`을 잡고 건너뜁니다.

### 응답

정상 처리되면 SVG에서 파싱되어 새로 저장된 `List<AreaResponse>`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| `eventId` 누락 | `AreaService.insertSvg(...)` | `400 Bad Request` |
| SVG 파일 누락 | `AreaService.insertSvg(...)` | `400 Bad Request` |
| 기존 배치가 있고 `force=false` | `AreaService.insertSvg(...)` | `409 Conflict` |
| SVG 읽기 또는 파싱 실패 | `normalizeSvgFile(...)`, `parseSvgAreas(...)` | `400 Bad Request` |
| SVG에서 등록 가능한 구역 없음 | `AreaService.insertSvg(...)` | `400 Bad Request` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 이벤트 배치 조회

브라우저에서 특정 이벤트의 SVG 배치 원본을 조회하는 시나리오입니다.

### 요청

```text
GET /ticket/api/v1/area/layout/event/{eventId}
권한: USER, ADMIN
```

### 처리 흐름

```text
Browser
  -> AreaController.selectLayout(...)
  -> AreaService.selectLayout(...)
  -> EventLayoutJpaRepository.findByEvent_EventId(...)
  -> EventLayout.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.selectLayout(...)`
   - URL path의 `eventId`를 받습니다.
   - `areaService.selectLayout(eventId)`를 호출합니다.
   - 응답이 있으면 `200 OK`, 없으면 `204 No Content`를 반환합니다.

2. `AreaService.selectLayout(...)`
   - `layoutJpaRepository.findByEvent_EventId(eventId)`로 배치 정보를 조회합니다.
   - 조회 결과가 있으면 `EventLayout.toResponse()`로 변환합니다.
   - 조회 결과가 없으면 `null`을 반환합니다.

### 응답

정상 조회되면 `EventLayoutResponse`를 `200 OK`로 반환합니다.

```json
{
  "layoutId": 1,
  "eventId": 1,
  "originalFileName": "layout.svg",
  "svgText": "<svg>...</svg>"
}
```

배치가 없으면 본문 없이 `204 No Content`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 인증 또는 권한 없음 | Spring Security | `401` 또는 `403` |

## 구역 단건 조회

브라우저에서 특정 구역 상세 정보를 조회하는 시나리오입니다.

### 요청

```text
GET /ticket/api/v1/area/select/id/{areaId}
권한: USER, ADMIN
```

### 처리 흐름

```text
Browser
  -> AreaController.selectById(...)
  -> AreaService.selectById(...)
  -> AreaRepositoryImpl.selectById(...)
  -> AreaJpaRepository.findById(...)
  -> Area.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.selectById(...)`
   - URL path의 `areaId`를 받습니다.
   - `areaService.selectById(areaId)`를 호출합니다.

2. `AreaService.selectById(...)`
   - 읽기 전용 트랜잭션에서 동작합니다.
   - `repository.selectById(id)`로 구역 엔티티를 조회합니다.
   - 조회 결과를 `toResponse()`로 변환합니다.

3. `AreaRepositoryImpl.selectById(...)`
   - `jpaRepository.findById(id)`로 구역을 찾습니다.
   - 구역이 없으면 `AreaNotExistException`을 발생시킵니다.

### 응답

정상 처리되면 `AreaResponse`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 구역이 존재하지 않음 | `AreaRepositoryImpl.selectById(...)` | `404 Not Found` |
| 인증 또는 권한 없음 | Spring Security | `401` 또는 `403` |

## 구역 조건 조회

브라우저에서 구역 목록을 검색 조건, 페이지, 정렬 정보로 조회하는 시나리오입니다.

### 요청

```text
POST /ticket/api/v1/area/select
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `AreaCondRequest`입니다.

```json
{
  "eventId": 1,
  "grade": "VIP",
  "status": "ACTIVE",
  "page": 0,
  "size": 10,
  "sort": ["areaName-asc"]
}
```

### 처리 흐름

```text
Browser
  -> AreaController.selectByCond(...)
  -> AreaService.selectByCond(...)
  -> AreaService.makeSortInfo(...)
  -> AreaRepositoryImpl.selectByCond(...)
  -> AreaRepositoryImpl.searchConditions(...)
  -> Area.toResponse(...)
  -> CustomPageResponse.of(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.selectByCond(...)`
   - JSON 요청 본문을 `AreaCondRequest`로 받습니다.
   - `areaService.selectByCond(cond)`를 호출합니다.

2. `AreaService.selectByCond(...)`
   - `cond.getPage()`, `cond.getSize()`, `makeSortInfo(cond.getSort())`로 `PageRequest`를 만듭니다.
   - `repository.selectByCond(cond, pageRequest)`로 조건 조회를 수행합니다.
   - 조회된 `Page<Area>`를 `Page<AreaResponse>`로 변환합니다.
   - `CustomPageResponse.of(...)`로 페이지 응답을 만듭니다.

3. `AreaRepositoryImpl.selectByCond(...)`
   - Querydsl로 검색 조건을 조립합니다.
   - `searchConditions(cond)`에서 조건 값이 있는 필드만 where 조건에 반영합니다.
   - `event`를 fetch join해서 구역 응답에 필요한 이벤트 정보를 함께 가져옵니다.
   - `offset`, `limit`, `orderBy`를 적용하고 count 쿼리로 전체 개수를 구합니다.

### 응답

정상 처리되면 `CustomPageResponse<AreaResponse>`를 `200 OK`로 반환합니다.

```json
{
  "content": [
    {
      "areaId": 1,
      "eventId": 1,
      "eventTitle": "아이유 콘서트",
      "areaName": "VIP",
      "grade": "VIP",
      "price": 165000,
      "status": "ACTIVE"
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
| 조건의 `eventId`가 존재하지 않음 | `AreaRepositoryImpl.eventIdEq(...)` | `404 Not Found` |
| 잘못된 정렬 방향 값 | `AreaService.makeSortInfo(...)` | `400 Bad Request` 또는 서버 예외 |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 수정

관리자가 구역 정보를 수정하는 시나리오입니다.

### 요청

```text
PUT /ticket/api/v1/area/update/id/{areaId}
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `UpdateAreaRequest`입니다.

```json
{
  "areaName": "VIP-A",
  "grade": "VIP",
  "price": 175000,
  "status": "ACTIVE"
}
```

### 처리 흐름

```text
Browser
  -> AreaController.update(...)
  -> AreaService.update(...)
  -> AreaRepositoryImpl.update(...)
  -> AreaRepositoryImpl.selectById(...)
  -> Area.update(...)
  -> Area.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.update(...)`
   - URL path의 `areaId`를 받습니다.
   - JSON 요청 본문을 `UpdateAreaRequest`로 받습니다.
   - `areaService.update(areaId, info)`를 호출합니다.

2. `AreaService.update(...)`
   - `repository.update(id, info)`를 호출합니다.
   - 반환된 구역 엔티티를 `toResponse()`로 변환합니다.

3. `AreaRepositoryImpl.update(...)`
   - `selectById(id)`로 수정할 구역을 조회합니다.
   - `area.update(info)`로 요청값이 있는 필드만 변경합니다.
   - 트랜잭션 커밋 시 변경 감지로 DB에 반영됩니다.

### 응답

정상 처리되면 수정된 `AreaResponse`를 `200 OK`로 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 수정 대상 구역 없음 | `AreaRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 단건 삭제

관리자가 특정 구역 하나를 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/area/delete/id/{areaId}
권한: ADMIN
```

### 처리 흐름

```text
Browser
  -> AreaController.delete(...)
  -> AreaService.delete(...)
  -> AreaRepositoryImpl.delete(...)
  -> AreaRepositoryImpl.selectById(...)
  -> AreaJpaRepository.delete(...)
  -> Area.toResponse(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.delete(...)`
   - URL path의 `areaId`를 받습니다.
   - `areaService.delete(areaId)`를 호출합니다.

2. `AreaService.delete(...)`
   - `repository.delete(id)`를 호출합니다.
   - 삭제된 구역 엔티티를 `toResponse()`로 변환합니다.

3. `AreaRepositoryImpl.delete(...)`
   - `selectById(id)`로 삭제할 구역을 조회합니다.
   - `jpaRepository.delete(area)`로 구역을 삭제합니다.
   - 삭제한 구역 엔티티를 서비스로 반환합니다.

### 응답

정상 처리되면 삭제된 구역 정보를 `AreaResponse`로 `200 OK` 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| 삭제 대상 구역 없음 | `AreaRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |

## 구역 다건 삭제

관리자가 여러 구역을 한 번에 삭제하는 시나리오입니다.

### 요청

```text
DELETE /ticket/api/v1/area/delete/bulk
Content-Type: application/json
권한: ADMIN
```

요청 본문은 `DeleteAreaBulkRequest`입니다.

```json
{
  "areaIds": [1, 2, 3]
}
```

### 처리 흐름

```text
Browser
  -> AreaController.deleteBulk(...)
  -> AreaService.deleteBulk(...)
  -> AreaService.delete(...)
  -> AreaRepositoryImpl.delete(...)
  -> AreaJpaRepository.delete(...)
  -> Browser
```

### 단계별 설명

1. `AreaController.deleteBulk(...)`
   - JSON 요청 본문을 `DeleteAreaBulkRequest`로 받습니다.
   - `areaService.deleteBulk(info)`를 호출합니다.
   - 서비스 호출이 끝나면 `ResponseEntity.ok().build()`로 본문 없는 응답을 반환합니다.

2. `AreaService.deleteBulk(...)`
   - `areaIds`가 비어 있으면 `IllegalArgumentException`을 발생시킵니다.
   - `areaIds`를 순회하면서 각 ID마다 `delete(id)`를 호출합니다.

3. `AreaService.delete(...)`
   - 단건 삭제와 같은 흐름으로 구역을 조회한 뒤 삭제합니다.

### 응답

정상 처리되면 응답 본문 없이 `200 OK`를 반환합니다.

### 주요 실패 케이스

| 상황 | 위치 | 결과 |
| --- | --- | --- |
| `areaIds`가 비어 있음 | `AreaService.deleteBulk(...)` | `400 Bad Request` |
| 삭제 대상 구역 없음 | `AreaRepositoryImpl.selectById(...)` | `404 Not Found` |
| 관리자 권한 없음 | Spring Security | `403 Forbidden` |
