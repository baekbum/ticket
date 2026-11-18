# 신규 PG 거래 경로로 레거시 API 정리

2026-11-18 진행 기록. 실제 DB의 payments, PG 미연결 계좌·카드 거래, 구형 PG 환불·입금/만료 Outbox는 모두 0건임을 읽기 전용으로 확인했다. 중지돼 있던 기존 local-postgres 컨테이너를 기동했으며 테이블 삭제·초기화·데이터 변경은 하지 않았다.

## 적용한 경계

- 브라우저 카드 승인·조회는 pgPaymentId 및 PG 세션이 필수다. 구형 paymentNo 전용 승인/조회 fallback을 제거했다. PG ID가 없는 캐시 데이터는 준비 정보 확인을 안내하고 금융 요청을 보내지 않는다.
- 예매 상세 환불은 /api/v1/reservation/{id}/refunds만 호출한다. PG ID가 없을 때 구형 PUT cancel로 우회하지 않는다.
- PG 구형 카드 승인·paymentNo 조회·환불·승인 존재 조회, 가상계좌 발급·조회·취소·환불, 구형 환불 resolve API를 폐쇄했다.
- 금융 컨트롤러의 비 gateway 카드 승인·조회·환불, 구형 은행 발급·조회·취소·환불 및 legacy/deposit를 폐쇄했다. 은행의 /api/v1/banks/virtual-accounts/deposit와 신규 가맹점·금융 API는 유지한다.
- 폐쇄 URL은 기본 보안 정책에서 거절한다. PG의 기존 X-Service-Token 필터 등록과 전용 허용 규칙을 제거했다. 사용자 JWT·가맹점 인증·금융 인증·입금 제공자 인증은 유지한다.
- 일반 예매 취소는 입금 전 전체 취소를 보존한다. 결제된 예매의 환불은 신규 환불 신청 API 사용을 안내하며 구형 환불 실행을 호출하지 않는다.
- 호출 그래프에서 외부 참조가 없는 구형 PG 서비스·고정 Ticksy Feign·구형 재처리 스케줄러 묶음은 제거했다. 신규 금융 서비스와 현재 Outbox·재시도 처리는 보존한다.

## 적용한 변경: Ticksy 구형 환불 오케스트레이션

자동 승인 검토가 금융 핵심 코드의 대량 삭제 위험을 이유로 중단한 뒤, 사용자가 아래 범위의 삭제와 회귀 검증을 명시적으로 승인했다. 승인된 구형 환불 오케스트레이션과 전용 의존성은 제거했다.

삭제 대상은 PaymentRefundProcessService의 다음 구형 메서드와 전용 의존성이다.

- markGatewaySucceeded, markGatewayFailed, markLocalFailed, completeCancellation, recoverPreviousRequest, startGatewayAttempt
- resumeSameRequest, prepareChangedRequest, hasGatewaySucceeded, resolvePreviousRequest
- createNewAttempt, createAttempt, loadCreatedAttempt, validateNewCancellation, attempt, calculation 등 위 흐름만 위한 보조 메서드
- 구형 PaymentGatewayRefundClient, PaymentRefundProcessCreator 및 더 이상 사용하지 않는 구형 환불 클라이언트·요청/응답 모델

보존 대상은 selectByCond, completeLocal의 공개/내부 메서드, 그 로컬 완료·롤백 기록·좌석/티켓/예매/쿠폰 반영 도우미다. 신규 ReservationRefundCommandService가 completeLocal을 호출한다. 입금 전 취소는 진행 중인 환불 존재 여부를 읽는 기능을 유지한다.

구체적 검증 범위는 신규 카드·은행 환불, 신청 스냅샷/동일 키 재시도, 철회와 성공 복구, NOWAIT 경합, 로컬 반영 실패 롤백, 입금 전 취소의 Outbox 저장·좌석 복원·입금 경합, 전체 결제 회귀 및 실행 파일 빌드다. 삭제 대상의 구형 전용 테스트는 현행 경로 테스트와 폐쇄 경로 거절 검증으로 대체하며 금융 성공을 임의로 로컬 완료 처리하지 않는다.

## 이번 변경에서 보존한 범위

- `PaymentRefundProcessService.completeLocal`의 공개·내부 구현은 기존 커밋과 동일하다. 조회·감사 로그·롤백 후 실패 기록과 그 의존성도 유지한다.
- `hasUnfinishedRequest`는 REQUESTED, GATEWAY_FAILED, GATEWAY_SUCCEEDED, LOCAL_FAILED, WITHDRAW_REQUESTED를 미완료로 판단한다. 입금 전 예매에 이 신청이 있으면 취소와 Outbox 저장을 모두 거절한다.
- 구형 PG 환불·Outbox 엔티티, 공유 DTO와 과거 상태값은 저장 데이터 및 스키마 호환을 위해 보존한다. 이번 변경은 DDL을 실행하지 않는다.
- Ticksy의 PG 미연결 계좌 취소 호환 분기·관리자 수동 계좌 정합성 조회는 이번 환불 실행 코드 삭제 범위에 포함하지 않았다. 그 분기의 cancel/status 전용 Feign도 남아 있다. 폐쇄한 PG URL로 신규 거래를 우회시키지 않으며, 신규 거래는 `PaymentGatewayTransactionClient`와 등록 가맹점 인증을 사용한다. 이 잔여 호환 그룹 정리는 별도 범위다.
- 브라우저가 캐시한 PG ID 없는 결제는 구형 승인·환불 API로 자동 우회하지 않는다. 준비 정보 확인을 안내하며, 사용자 환불의 유효성 검증·소유권 검증은 신규 환불 경로에서 수행한다.

## 검증 결과

- 공통 모듈 45개, PG 전체 270개 통과. PG에는 실제 PostgreSQL 스키마·매핑 검증 29개가 포함되며 건너뜀은 없다.
- Ticksy 전체 729개 중 17개는 `TICKET_REDIS_INTEGRATION=true`가 필요한 좌석 Redis 테스트로 미실행했다. 실행한 712개 중 한 건은 폐쇄 경로의 구형 안내 문구를 기대해 실패했다. 기대 문구만 현행 계약으로 수정한 뒤 해당 `VirtualAccountIssuanceCheckoutIntegrationTest` 31개를 재실행하여 모두 통과했다. 나머지 전체 실행 결과와 합쳐 실행된 712개는 성공이다.
- 신규 카드·은행 환불, 같은 신청 재시도·다른 스냅샷 거절, 철회·기존 성공 복구, NOWAIT 충돌, 로컬 완료 롤백, 입금 전 취소 Outbox 및 입금 경합을 포함한다. 미완료 환불이 있는 입금 전 예매의 취소 차단도 추가 검증했다.
- 프런트엔드 44개 테스트와 빌드 통과. PG·Ticksy bootJar 빌드 통과. 실행 파일에 삭제한 구형 서비스가 없고 신규 서비스·취소 Outbox가 남아 있음을 확인했다.
- 기존 PostgreSQL의 스키마 검증은 DDL을 실행하지 않으며 테스트 데이터는 롤백한다. 나머지 PostgreSQL 통합 검증은 격리한 임시 컨테이너를 사용하고 종료했다. 기존 DB 테이블 삭제·초기화·영구 데이터 변경은 없다.
- 실제 브라우저·Bruno로 세 서비스를 함께 기동한 수동 결제 시나리오는 이번 검증에 포함하지 않았다.
