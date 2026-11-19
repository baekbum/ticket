package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayVirtualAccountCancellationService {

    // 짧은 로컬 트랜잭션의 접수·조회. 은행 HTTP는 Outbox 워커가 담당한다.
    private final GatewayVirtualAccountCancelCommands commands;

    /**
     * 취소를 접수한다. 최초 요청은 완료가 아닌 PROCESSING을 반환한다.
     */
    public GatewayVirtualAccountCancellationResponse cancel(
            AuthenticatedMerchant merchant,
            String pgPaymentId,
            GatewayVirtualAccountPaymentCancelRequest request
    ) {

        return commands.enqueue(merchant, GatewayPaymentIdNormalizer.normalize(pgPaymentId), request);
    }

    /**
     * 인증된 소유 가맹점에 취소 접수 또는 최종 결과를 반환한다.
     */
    public GatewayVirtualAccountCancellationResponse find(AuthenticatedMerchant merchant, String pgPaymentId) {

        return commands.find(merchant, GatewayPaymentIdNormalizer.normalize(pgPaymentId));
    }
}
