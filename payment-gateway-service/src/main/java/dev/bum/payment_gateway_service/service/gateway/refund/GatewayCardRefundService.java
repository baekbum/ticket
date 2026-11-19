package dev.bum.payment_gateway_service.service.gateway.refund;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardRefundService {

    // HTTP 앞뒤로 분리한 PG 로컬 트랜잭션.
    private final GatewayCardRefundCommands commands;

    // 같은 요청 키로 최대 네 번 호출하는 카드사 HTTP 경계.
    private final GatewayCardRefundClient card;

    /**
     * 확정 결과는 재응답하고 미완료 요청만 카드사에 전달한다. PG DB 락은 HTTP 전에 해제된다.
     */
    public PaymentRefundResult execute(
            AuthenticatedMerchant merchant,
            String pgPaymentId,
            String refundRequestId,
            GatewayPaymentRefundRequest request,
            boolean withdraw
    ) {

        CardRefundCommand command = request.toCard(GatewayPaymentIdNormalizer.normalize(pgPaymentId), refundRequestId);
        PaymentRefundResult existing = commands.prepare(merchant, command, request);

        if (existing != null) {
            return existing;
        }

        return commands.confirm(command, card.send(command, withdraw));
    }
}
