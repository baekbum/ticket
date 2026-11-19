package dev.bum.payment_gateway_service.service.gateway.payment;

import dev.bum.common.service.paymentgateway.payment.*;
import dev.bum.payment_gateway_service.dto.bank.BankAccountIssueGateResponse;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayBankMethodChangeService {

    private final GatewayBankMethodChangeCommands commands;

    private final FinancialSimulationGatewayService financial;

    public GatewayBankMethodChangeResponse change(AuthenticatedMerchant merchant, String pgPaymentId, GatewayBankMethodChangeRequest request) {

        String id = GatewayPaymentIdNormalizer.normalize(pgPaymentId);
        GatewayBankMethodChangeCommands.Preparation preparation = commands.prepare(merchant, id, request);

        if (preparation.replay() != null) {
            return preparation.replay();
        }

        BankAccountIssueGateResponse result = financial.closeGatewayAccountIssue(preparation.source());

        if (!result.closed()) {
            throw new GatewayPaymentConflictException("이미 계좌가 발급되어 결제수단을 변경할 수 없습니다.");
        }

        return commands.switched(merchant, id, request);
    }
}
