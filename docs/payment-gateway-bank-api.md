# 은행 입금 API와 PG 통보

> 2026-11-18: 구형 paymentNo 전용 PG/금융 API와 X-Service-Token 전용 PG 인증은 폐쇄했다. 아래의 구형 경로 설명은 이전 구조 기록이며 호출 계약으로 사용하지 않는다. 현재 변경 범위와 보존한 API는 [레거시 정리 기록](payment-legacy-retirement.md)을 따른다. 은행 입금은 `/api/v1/banks/virtual-accounts/deposit`, 환불은 신규 PG 거래·가맹점 범위 API를 사용한다.

입금은 은행 업무이므로 BankController에서 처리한다. Postman·Bruno는 입금망에서 발생하는 요청을 재현할 때 같은 API를 호출하며, 별도 테스트 컨트롤러나 support API를 두지 않는다.

## 호출 방향

~~~text
입금 제공자 / Postman / Bruno
  → BankController.deposit()
  → 은행 계좌 DEPOSITED + 입금 이력 + 은행 Outbox 커밋
  → BankDepositOutboxDispatcher
  → HTTP로 PG 입금 통보
  → PG 입금·이력·가맹점 통보 Outbox 커밋
  → Ticksy

취소: Ticksy → PG 접수 → PG의 은행 요청 Outbox → BankController → 은행 확정 결과
결과: PG 취소 결과 커밋 → PG의 가맹점 결과 Outbox → Ticksy
~~~

은행 입금 수신 트랜잭션에서 PG나 Ticksy HTTP를 바로 호출하지 않는다. 은행 입금·이력·Outbox는 함께 커밋/롤백한다. 이후 PG 정상 확인 응답을 받아야 은행 Outbox가 DELIVERED가 되며, PG→Ticksy 전달은 독립 재시도한다. 중복·유실·지연 통보·입금/취소 경합 처리와 실제 자금 반환 없음은 기존 계약을 유지한다.

## 은행 입금 요청

POST /api/v1/banks/virtual-accounts/deposit

~~~http
X-Payment-Provider-Token: {입금 제공자 토큰}
Content-Type: application/json
~~~

~~~json
{
  "accountNumber": "1111-123456",
  "depositorName": "입금자",
  "amount": 10000,
  "depositedAt": "2026-11-11T12:00:00"
}
~~~

depositedAt을 생략하면 은행 서버에서 최초 시각을 확정한다. PG 연결 계좌를 대상으로 하며 정상 응답은 BankVirtualAccountDepositResponse다. 은행의 pgPaymentId·은행·계좌·금액·입금자·입금 시각·상태를 반환하고 PG 상태나 가맹점 전달 완료를 보장하지 않는다. PG는 별도 소유 가맹점 조회 API로 확인한다.

기존 PG 미연결 계좌는 POST /api/v1/banks/virtual-accounts/legacy/deposit에서 기존 FinancialVirtualAccountDepositResponse를 반환한다. 이 임시 호환 경로는 기존 자료 정리가 끝난 뒤 제거할 수 있다. 일반 사용자·가맹점·내부 서비스 토큰만으로 은행 입금을 발생시킬 수 없으며 기존 입금 제공자 인증을 유지한다.

## 입금 처리 서비스의 트랜잭션

BankVirtualAccountDepositService는 클래스에 @Transactional을 두고 기본 REQUIRED 전파를 사용한다. deposit·enqueueExistingDeposit·rejectDeposit은 상위 트랜잭션에 참여하며, 상위 트랜잭션이 없으면 각 호출에서 시작/종료한다. find는 메서드의 readOnly=true를 유지한다. 현재 호출 경로에 별도 커밋이 필요한 메서드가 없어 이 서비스의 REQUIRES_NEW는 제거했다. HTTP 은행 요청과 Outbox 워커의 NEVER 경계 때문에 기존 정상 호출에서도 짧은 은행 트랜잭션으로 동작한다.

GatewayVirtualAccountDepositCommands도 클래스에 @Transactional을 두고 confirm의 REQUIRES_NEW를 제거했다. PG 입금 상태·최초 입금 이력·가맹점 통보 Outbox는 상위 트랜잭션에 함께 참여하며 상위 롤백 때 함께 롤백된다. 현재 HTTP 수신 컨트롤러에는 상위 트랜잭션이 없어 서비스 반환 전에 PG 수신 커밋이 끝난다. 이미 커밋된 은행 입금과 은행 Outbox는 PG 롤백으로 되돌아가지 않고, 은행의 동일 이벤트 재시도로 복구한다.

## PG의 은행 업무와 인증

PG의 발급·조회·취소·환불·취소 예매 계좌 정리는 기존 /internal/financial-simulations/banks/virtual-accounts 하위 URL과 금융 전용 토큰을 유지한다. 같은 BankController에서 처리하되 입금 제공자용 은행 진입점과 URL/권한을 구분한다. 클래스에 공통 경로를 두지 않고 두 경로 상수를 각 매핑에 명시해 내부 계약을 변경하지 않는다.

은행→PG 통보 수신 POST /internal/financial-simulations/gateway/bank-deposits는 controller.gateway.deposit.GatewayBankEventController에서 유지한다. 카드 승인·취소·환불·조회도 기존 CardController와 PG 경로를 유지한다.

## 제거와 호환성

BankDepositTestController와 support 입금 URL을 제거했다. PG의 입금 우회 API 및 은행 내부 입금 별칭도 다시 추가하지 않는다. 기존 호출자는 은행의 위 입금 URL로 변경한다. 은행 서비스·Outbox·DB 스키마나 실제 데이터는 변경하지 않는다.

## 검증 결과 (2026-11-11)

PG 서비스·보안·설정 회귀 282개(28개 클래스)가 실패·오류·건너뜀 없이 통과했다. 은행의 PG 연결/기존 계좌 입금, 입금 제공자 인증과 사용자·일반 서비스 토큰 차단, 제거된 support/PG 우회 입금 경로 차단, 기존 PG→은행 내부 URL 유지, 입금·취소·Outbox의 중복/경합/롤백/재시도 및 카드 업무 HTTP 회귀를 포함한다.

DB와 금융 서비스·Outbox 스키마는 변경하지 않았다. 검증은 H2 통합 테스트이며 실제 DB 삭제·초기화는 수행하지 않았다. 은행 컨트롤러 코드 컨벤션 검사와 git diff --check가 통과했다.

은행 입금 서비스의 기본 REQUIRED 전파 변경 후 입금·취소 통합 테스트 54개를 별도로 실행해 모두 통과했다. 새 검증은 상위 롤백 시 은행 입금·입금 이력·Outbox의 공동 롤백과 취소 예매 계좌 정리의 상위 트랜잭션 참여를 확인한다.

PG 입금 처리도 기본 REQUIRED 전파로 변경한 후 입금·취소 통합 55개가 모두 통과했다. 상위 롤백 시 PG 상태·입금 이력·가맹점 통보 Outbox가 함께 롤백되고, 이미 커밋된 은행 입금·은행 Outbox는 유지되어 동일 이벤트 재시도로 복구됨을 검증했다.
