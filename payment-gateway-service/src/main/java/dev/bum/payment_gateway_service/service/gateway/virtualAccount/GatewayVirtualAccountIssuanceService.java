package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayVirtualAccountIssuanceService {

    // HTTP 전후의 짧은 PG 검증/확정 트랜잭션을 실행한다.
    private final GatewayVirtualAccountIssueCommandService commands;
    // 은행 컨트롤러는 실제 HTTP로 호출한다.
    private final FinancialSimulationGatewayService financial;

    /**
     * 저장된 금융 성공을 우선 복구하고, 신규 발급이 필요한 경우에만 은행에 같은 PG ID를 전달한다.
     */
    public GatewayVirtualAccountPaymentIssueResponse issue(
            AuthenticatedMerchant principal,
            String pgPaymentId,
            GatewayVirtualAccountPaymentIssueRequest request
    ) {

        String paymentId = GatewayPaymentIdNormalizer.normalize(pgPaymentId);
        GatewayVirtualAccountIssuePreparation preparation = commands.inspect(principal, paymentId, request);

        if (preparation.replay() != null) {
            return preparation.replay();
        }

        Optional<BankVirtualAccountIssueResponse> existing = financial.gatewayAccountStatus(paymentId);
        BankVirtualAccountIssueResponse bank;

        if (existing.isPresent()) {
            bank = existing.get();
        } else {
            if (!preparation.activeMerchant()) {
                throw new AccessDeniedException("신규 계좌를 발급할 수 없는 가맹점입니다.");
            }

            if (!preparation.bankRequest().expiresAt().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("입금 기한이 만료되어 계좌를 발급할 수 없습니다.");
            }

            bank = financial.issueGatewayAccount(preparation.bankRequest());
        }

        return commands.confirm(principal, paymentId, request, bank);
    }
}
