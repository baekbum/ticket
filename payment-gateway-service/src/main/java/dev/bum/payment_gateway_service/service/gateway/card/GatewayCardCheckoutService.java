package dev.bum.payment_gateway_service.service.gateway.card;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.security.card.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardCheckoutService {

    private final GatewayCardCheckoutCommands commands;

    private final GatewayCardSessionToken tokens;

    private final GatewayCardApprovalService approvals;

    public GatewayCardSessionResponse issue(
            AuthenticatedMerchant merchant,
            String id,
            GatewayCardSessionRequest request
    ) {

        return commands.issue(merchant, GatewayPaymentIdNormalizer.normalize(id), request);
    }

    public GatewayCardApprovalResponse approve(
            String userId,
            String id,
            String token,
            GatewayCardPaymentApproveRequest request
    ) {

        String normalized = GatewayPaymentIdNormalizer.normalize(id);
        GatewayCardSessionClaims session = tokens.verify(token, userId, normalized, true);

        if (!request.getPaymentNo().equals(session.paymentNo())) {
            throw new GatewayPaymentConflictException("세션의 결제번호와 일치하지 않습니다.");
        }

        AuthenticatedMerchant merchant = commands.validate(session);
        GatewayCardApprovalResponse response = approvals.approve(merchant, normalized,
                new GatewayCardApprovalRequest(merchant.merchantId(), userId, request.getCardCompany(),
                        request.getCardNumber(), request.getCvc(), request.getCardPassword(), request.getAmount()));

        return response;
    }

    public GatewayCardApprovalResponse find(String userId, String id, String token) {

        String normalized = GatewayPaymentIdNormalizer.normalize(id);
        GatewayCardSessionClaims session = tokens.verify(token, userId, normalized, false);

        return approvals.recoverStatus(commands.validate(session), normalized);
    }
}
