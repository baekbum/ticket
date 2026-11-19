# 구형 결제 모델·설정 정리

2026-11-18. 사용자는 정보 테이블을 제외한 예매·결제 이력의 기존 데이터 삭제를 허용했다. 실제 사용처를 기준으로 구형 전용 데이터만 정리하며 현재 거래를 재시도할 때 필요한 원거래·환불·Outbox는 유지한다.

## 제거 범위

| 대상 | 처리 |
|---|---|
| `gateway_refunds` | 구형 paymentNo 기반 PG 환불 모델·레포지토리·DDL 및 실제 테이블 제거 |
| `virtual_account_outbox_events` | 구형 Kafka 만료 전달 모델·레포지토리·DDL 및 실제 테이블 제거 |
| `virtual_account_deposit_outbox` | 구형 PG→Ticksy 입금 전달 모델·레포지토리·DDL 및 실제 테이블 제거 |
| `dummy_card_payment_histories.ticket_completed_at` | 금융 승인 상태와 Ticksy 통보 결과를 섞던 컬럼·변경 메서드 제거 |
| 금융 행의 `pg_payment_id` | 카드 승인·실패 거래 및 은행 계좌에서 필수로 전환. 미연결 생성 경로·조회·복구 쿼리 제거 |
| `TICKET_PAYMENT_COMPLETED`, `TICKET_PAYMENT_FAILED` | 카드 금융 상태에서 제거. 통보 상태는 현재 PG 통보 Outbox가 담당 |
| `SUPERSEDED` | 자동 환불 요청 대체 상태·변경 메서드·구형 롤백 분기·화면 표시 제거. 현재 재시도·철회 상태 유지 |

현재 호출자가 없는 구형 카드 검증·완료·실패·정산 REST 콜백 및 단일 인자 정산 메서드를 제거한다. 신규 카드 통보는 가맹점·PG ID·소유자·금액 검증 및 예매→결제 잠금 순서를 유지한다. 현재 통보에서 사용하는 `CardPaymentCompleteRequest`, `CardPaymentSettlementResponse`와 은행의 입금 테스트 요청은 보존한다.

미사용 공통 Kafka 이벤트, 구형 환불 resolve·은행 조회/취소 DTO, 금융 응답 중계 래퍼·예외와 그 전용 핸들러를 제거한다. 현재 금융 실패·저장 실패·승인 거절·가맹점 충돌 오류 계약은 유지한다.

## 데이터·설정 보호

- 정보 테이블(`users`, `events`, `areas`, `seats` 등), 카드 원장, 가맹점·통보 대상 설정은 초기화하지 않는다.
- DB 변경은 한 로컬 트랜잭션에서 수행하고 `CASCADE`로 다른 테이블을 삭제하지 않는다. 정보 테이블을 포함한 변경 대상 외 모든 public 테이블의 행 수·행 내용 해시·컬럼 정의를 변경 전후 비교하여 다르면 롤백한다.
- 미연결 금융 행이 있으면 해당 구형 행과 연결 이력만 정리한다. 현재 PG 원거래·은행 입금 사실·금융 환불 영수증·통보 Outbox는 삭제하지 않는다. 사전 조사에서 구형 테이블 3개, 미연결 카드/계좌, SUPERSEDED 환불은 각각 0건이었다.
- 과거 카드의 Ticksy 통보 성공/실패 상태는 금융 승인 사실인 APPROVED로 정리한다. 카드 원장의 잔액·한도는 변경하지 않는다.
- 기본·운영 설정의 현재 통보 암호화 키와 `app.ticket.service-token` 호환 키는 실제 암호화에 사용하므로 유지한다. 고정 Ticksy URL 및 제거된 카드 복구·은행 스케줄러 설정만 제거한다.
- 최종 DDL은 PG의 `schema.sql`에 반영한다. 초기화 SQL 전체를 기존 DB에 실행하지 않는다.

## 실제 DB 적용

local-postgres/ticket에 변경분만 적용하고 커밋했다. 구형 테이블 3개를 삭제했고 `ticket_completed_at` 삭제 및 금융 원거래 2개의 PG ID NOT NULL을 적용했다. 미연결 금융 행·SUPERSEDED 환불·과거 카드 통보 상태는 모두 0건이라 현재 거래 데이터 삭제·상태 변환은 발생하지 않았다. 변경 대상 외 56개 테이블의 행 수·행 해시·컬럼 정의가 모두 동일했고 그 안에 정보·원장·가맹점 설정 7개도 포함된다.

Ticksy schema.sql에는 제거된 상태의 CHECK/ENUM 정의가 없어 구조 변경이 필요하지 않다. 최종 PG schema.sql에서 구형 DDL과 컬럼을 제거했다. 재사용할 마이그레이션 파일은 추가하지 않는다.

## 검증

- 공통 45개·PG 271개·Ticksy 682개, 총 998개 실행 통과. 실패·오류 0개. 실제 PostgreSQL을 사용하는 checkout·수단 전환·환불 NOWAIT 및 PG 스키마 검증도 실행했다.
- Ticksy 좌석 Redis 전용 17개는 `TICKET_REDIS_INTEGRATION=true` 조건을 활성화하지 않아 제외했다. PostgreSQL 결제 테스트는 제외하지 않았다.
- 카드 승인·보상·부분 환불·가맹점 격리, 계좌 발급·입금·만료·취소 경합, 중복·지연 통보·응답 유실·동일 키 조건 불일치·Outbox 저장 실패 롤백 회귀를 확인했다.
- 프런트엔드 테스트 44개와 TypeScript/Vite 빌드 통과. PG·Ticksy bootJar 빌드 통과. 공통 JAR와 두 bootJar에 이번 삭제 대상 클래스가 남아 있지 않음을 확인했다.
- 최종 PG·Ticksy schema.sql의 47개 테이블·493개 컬럼·134개 제약·59개 명시적 인덱스가 실제 DB와 일치한다.
- 테스트 전용 PostgreSQL 컨테이너는 종료·제거했다. 실제 DB는 시작 전 실행 상태를 유지한다.
- 브라우저·전체 서비스를 실행한 수동 결제·환불과 실은행 송금은 이번 검증 범위에 포함하지 않았다.
