# ticket-service

브라우저 클라이언트가 티켓팅 관련 작업을 요청했을 때, `ticket-service` 백엔드에서 어떤 흐름으로 처리되는지 정리합니다.

상세 시나리오는 컨트롤러 도메인별 문서에 나누어 기록합니다.

## 시나리오 문서

- [이벤트 시나리오](docs/scenarios/event.md)
- [구역 시나리오](docs/scenarios/area.md)
- [좌석 시나리오](docs/scenarios/seat.md)
- [예약 시나리오](docs/scenarios/reservation.md)

## 작성 기준

각 시나리오는 나중에 빠르게 다시 읽기 쉽도록 다음 정보만 기록합니다.

- 클라이언트가 호출하는 API
- 진입 컨트롤러와 주요 서비스 메서드
- 내부에서 발생하는 핵심 작업
- 최종 응답 형태
- 주요 실패 케이스
