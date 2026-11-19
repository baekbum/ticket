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
public class GatewayBankRefundService {

    // HTTP 앞뒤로 분리한 PG 로컬 트랜잭션.
    private final GatewayBankRefundCommands commands;

    // 같은 요청 키로 최대 네 번 호출하는 은행 HTTP 경계.
    private final GatewayBankRefundClient bank;

    /**
     * 확정 결과는 재응답하고 미완료 요청만 은행에 전달한다. PG DB 락은 HTTP 전에 해제된다.
     */
    public BankRefundResult execute(
            AuthenticatedMerchant merchant,
            String pgPaymentId,
            String refundRequestId,
            GatewayBankRefundRequest request,
            boolean withdraw
    ) {

        BankRefundCommand command = request.toBank(GatewayPaymentIdNormalizer.normalize(pgPaymentId), refundRequestId);
        BankRefundResult existing = commands.prepare(merchant, command, request);

        if (existing != null) {
            return existing;
        }

        return commands.confirm(command, bank.send(command, withdraw));
    }
}
