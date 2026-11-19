package dev.bum.payment_gateway_service.service.gateway.payment;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalGateResponse;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApprovalCommandService;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayPaymentMethodChangeService {

    private final GatewayPaymentMethodChangeCommands commands;

    private final FinancialSimulationGatewayService financial;

    private final GatewayCardApprovalCommandService approvals;

    public GatewayCardMethodChangeResponse change(AuthenticatedMerchant merchant, String pgPaymentId, GatewayCardMethodChangeRequest request) {

        String id = GatewayPaymentIdNormalizer.normalize(pgPaymentId);
        GatewayPaymentMethodChangeCommands.Preparation preparation = commands.prepare(merchant, id, request);

        if (preparation.replay() != null) {
            return preparation.replay();
        }

        FinancialCardApprovalGateResponse result = financial.closeGatewayCardApproval(preparation.source());

        if (!result.closed()) {
            approvals.confirmRecovered(merchant, id, result.approval());

            return commands.approved(merchant, id, request);
        }

        return commands.switched(merchant, id, request);
    }
}
