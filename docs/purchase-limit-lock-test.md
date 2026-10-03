# 구매 매수 제한 동시성 검증

`TicketPurchaseLockIntegrationTest`는 실제 JPA 예매 저장 로직과 독립된 DB 트랜잭션을 사용한다.

- 같은 사용자의 서로 다른 좌석 요청을 동시에 처리해 카운트와 티켓 모두 제한 초과를 차단한다. 2매 제한에서 각각 1매씩 요청하면 둘 다 성공한다.
- 최초 잠금 행 생성 경쟁과 기존 잠금 행 재사용을 모두 검증한다.
- 공연 그룹 제한은 서로 다른 회차에서도 적용한다.
- 첫 요청이 롤백되면 대기하던 요청은 남은 매수로 정상 예매한다.
- 다른 사용자와 회차별 제한의 다른 회차는 서로 차단하지 않는다.
- 예매 트랜잭션 없이 잠금을 획득하는 호출은 거부한다.
- 취소·만료·부분 취소·관리자 상태 보정에서 카운트가 실제 티켓 상태와 함께 변경되는지 검증한다.
- 차감 롤백과 중복 차감 방지, 기존 티켓이 모두 취소된 사용자 카운트의 0 초기화를 검증한다.

기본 실행은 PostgreSQL 호환 모드의 H2를 사용한다.

```powershell
.\gradlew.bat :ticket-service:test --tests '*TicketPurchaseLockIntegrationTest'
```

PostgreSQL에서도 검증하려면 전용 테스트 컨테이너를 실행한다. 아래 DB는 테스트가 스키마를 생성하고 삭제하므로 테스트 전용으로만 사용한다.

```powershell
docker run --detach --rm --name ticket-purchase-lock-verification -p 127.0.0.1::5432 -e POSTGRES_USER=ticket_test -e POSTGRES_PASSWORD=ticket_test -e POSTGRES_DB=ticket_purchase_lock_test postgres:17.5
docker exec ticket-purchase-lock-verification pg_isready -U ticket_test -d ticket_purchase_lock_test
$env:TICKET_TEST_POSTGRES_PORT = (docker port ticket-purchase-lock-verification 5432/tcp).Split(':')[-1]
.\gradlew.bat :ticket-service:test --tests '*TicketPurchaseLockIntegrationTest' --rerun-tasks
Remove-Item Env:TICKET_TEST_POSTGRES_PORT
docker stop ticket-purchase-lock-verification
```

테스트는 Redis·PG HTTP 호출을 포함하지 않는다. 신규 DB에는 schema.sql을 적용한다. 기존 잠금 테이블이 있는 DB는 예매 관련 쓰기를 중단한 상태에서 sql/add-ticket-purchase-count.sql을 적용한 후 변경된 서비스를 배포해야 한다. 이 스크립트는 ticket_count 컬럼을 추가하고 기존 행을 유효 티켓 수로 초기화한다.
