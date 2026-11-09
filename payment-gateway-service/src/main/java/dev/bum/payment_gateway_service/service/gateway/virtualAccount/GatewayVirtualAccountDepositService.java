package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.gateway.GatewayVirtualAccountPaymentDepositResponse;
import dev.bum.payment_gateway_service.dto.financial.FinancialVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
public class GatewayVirtualAccountDepositService {

    // 계좌에서 PG 식별자만 찾는다. 요청 본문에서 가맹점/수단을 신뢰하지 않는다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 은행 도메인에 PG 이력 판단을 맡기지 않고 PG 저장소가 미확정 입금을 대사한다.
    private final GatewayPaymentJpaRepository payments;
    // 금융 HTTP 전후의 짧은 PG 트랜잭션.
    private final GatewayVirtualAccountDepositCommands commands;
    // 은행 계좌 처리와 결과 조회는 기존 HTTP 경계를 유지한다.
    private final FinancialSimulationGatewayService financial;

    /**
     * 기존 미연결 계좌의 계약을 보존하고 PG 연결 계좌만 새 입금 흐름으로 보낸다.
     */
    public GatewayVirtualAccountPaymentDepositResponse deposit(GatewayVirtualAccountDepositRequest request) {

        Optional<String> linked = accounts.findPgPaymentIdByAccountNumber(request.getAccountNumber());

        if (linked.isEmpty()) {
            FinancialVirtualAccountDepositResponse response = financial.deposit(request);

            if (!request.getAccountNumber().equals(response.accountNumber()) || response.amount() == null
                    || request.getAmount().compareTo(response.amount()) != 0) {
                throw new FinancialProviderResponseException();
            }

            return GatewayVirtualAccountPaymentDepositResponse.from(response);
        }

        String id = linked.get();
        commands.inspect(id, request);

        return commands.confirm(id, financial.depositGatewayAccount(request));
    }

    /**
     * 은행 입금 커밋 후 HTTP 응답 유실·프로세스 종료·PG 저장 실패를 같은 금융 사실로 복구한다.
     */
    @Scheduled(fixedDelayString = "${app.virtual-account.gateway-deposit-recovery.fixed-delay-ms:30000}")
    public void recoverDeposits() {

        payments.findUnconfirmedDepositIds(PageRequest.of(0, 100)).forEach(id -> {
            try {
                financial.gatewayDepositStatus(id).ifPresent(bank -> commands.confirm(id, bank));
            } catch (RuntimeException failure) {
                log.warn("PG 입금 결과 복구 실패: pgPaymentId={}, errorType={}", id, failure.getClass().getSimpleName());
            }
        });
    }
}
