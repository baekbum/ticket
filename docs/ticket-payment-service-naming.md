# Ticksy 결제 서비스 이름 규칙

2026-11-17. Ticksy 서비스 클래스는 연결 대상인 PG 대신 수행하는 업무를 기준으로 이름을 붙인다.

- Service: checkout 준비·전환·발급, 환불, 결과 통보의 흐름을 조율한다.
- CommandService: 해당 흐름의 Ticksy DB 조회·변경 및 트랜잭션 경계를 담당한다.
- Scheduler·Preparation·Candidate·Response: 예약 실행 또는 로컬 처리 모델의 역할을 표현한다.
- PaymentGatewayTransactionClient 등 Feign Client와 PG 통신용 DTO·보안 설정은 연결 대상을 나타내는 이름을 유지한다.

| 이전 이름 | 변경 이름 |
|---|---|
| GatewayCardToBankCheckoutService | CardToBankCheckoutService |
| GatewayBankToCardCheckoutService | BankToCardCheckoutService |
| GatewayMethodChangeCheckoutCommands | CheckoutMethodChangeCommandService |
| GatewayCardCheckoutService | CardCheckoutService |
| GatewayCardCheckoutCommands | CardCheckoutCommandService |
| GatewayVirtualAccountCheckoutService | VirtualAccountIssuanceCheckoutService |
| GatewayVirtualAccountCheckoutCommands | VirtualAccountIssuanceCheckoutCommandService |
| GatewayAccountIssuePreparation | VirtualAccountIssuePreparation |
| GatewayCardCheckoutPreparation | CardCheckoutPreparation |
| GatewayReservationRefundService | ReservationRefundService |
| GatewayReservationRefundCommands | ReservationRefundCommandService |
| GatewayRefundActionResponse | RefundActionResponse |
| GatewayVirtualAccountExpirationScheduler | VirtualAccountExpirationScheduler |
| GatewayVirtualAccountExpirationCommands | VirtualAccountExpirationCommandService |
| GatewayVirtualAccountExpirationCandidate | VirtualAccountExpirationCandidate |
| GatewayPaymentNotificationService | PaymentNotificationService |
| GatewayCardNotificationService | CardPaymentNotificationService |
| GatewayBankNotificationService | BankDepositNotificationService |
| GatewayBankCancellationNotificationService | BankCancellationNotificationService |

패키지와 HTTP 경로·메서드·JSON 필드, 인증 헤더, 설정 키, 트랜잭션 경계, 스키마 및 데이터는 변경하지 않는다. Spring 주입, 컨트롤러·서비스·테스트 참조는 새 클래스 이름을 사용한다. PaymentRefundProcessGatewayAttempt처럼 PG 호출 시도 자체를 나타내는 이름은 유지한다.

검증: checkout·결제·환불·만료·통보 관련 회귀 테스트 319개가 전부 통과했다(실패·건너뜀 0, 실제 PostgreSQL 통합 테스트 포함). Ticksy bootJar 빌드도 통과했다. 소스·리소스의 이전 타입 참조와 명시적 빈 이름 참조가 없으며 실행 파일에도 이전 Gateway 접두사 서비스 클래스가 남아 있지 않다. 브라우저 수동 검증은 수행하지 않았다.
