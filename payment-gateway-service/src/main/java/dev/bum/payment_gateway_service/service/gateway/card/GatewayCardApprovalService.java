package dev.bum.payment_gateway_service.service.gateway.card;

import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentNotFoundException;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import dev.bum.payment_gateway_service.service.gateway.notification.MerchantNotificationTargetResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Service("pgCardApprovalService")
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardApprovalService {

    // HTTP 전후의 PG 검증/확정 트랜잭션.
    private final GatewayCardApprovalCommandService commands;
    // 별도 금융 요청과 트랜잭션으로 실행하는 카드사 HTTP.
    private final FinancialSimulationGatewayService financial;
    // 새 금융 실행에만 통보 설정을 필수로 확인한다.
    private final MerchantNotificationTargetResolver targets;

    /**
     * 기존 금융 성공을 조회/복구하고 최초 실행에서만 새 승인을 요청한다.
     */
    public GatewayCardApprovalResponse approve(
            AuthenticatedMerchant merchant,
            String pgPaymentId,
            GatewayCardApprovalRequest request
    ) {

        String id = GatewayPaymentIdNormalizer.normalize(pgPaymentId);
        GatewayCardApprovalPreparation preparation = commands.inspect(merchant, id, request);

        if (preparation.replay() != null) {
            return preparation.replay();
        }

        Optional<FinancialCardApprovalResponse> existing = financial.gatewayCardStatus(id);
        FinancialCardApprovalResponse card;

        if (existing.isPresent()) {
            card = existing.get();
        } else {
            if (!preparation.activeMerchant()) {
                throw new AccessDeniedException("신규 카드 승인을 실행할 수 없는 가맹점입니다.");
            }

            if (!preparation.financialRequest().expiresAt().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("카드 승인 기한이 만료되었습니다.");
            }

            targets.resolve(merchant.merchantId(), preparation.notificationContract(), preparation.notificationTargetKey());
            card = financial.approveGatewayCard(preparation.financialRequest());
        }

        return commands.confirm(merchant, id, request, card);
    }

    /**
     * 응답 유실 뒤 조회만으로 금융 성공을 복구한다. 금융 재승인이나 카드 비밀값 저장은 하지 않는다.
     */
    public GatewayCardApprovalResponse recoverStatus(AuthenticatedMerchant merchant, String pgPaymentId) {

        String id = GatewayPaymentIdNormalizer.normalize(pgPaymentId);
        Optional<GatewayCardApprovalResponse> existing = commands.findApproved(merchant, id);

        if (existing.isPresent()) {
            return existing.get();
        }

        FinancialCardApprovalResponse card = financial.gatewayCardStatus(id)
                .orElseThrow(GatewayPaymentNotFoundException::new);

        return commands.confirmRecovered(merchant, id, card);
    }

    /**
     * 등록 가맹점의 원승인과 통보 상태를 조회한다.
     */
    public GatewayCardApprovalResponse status(AuthenticatedMerchant merchant, String pgPaymentId) {

        return commands.find(merchant, GatewayPaymentIdNormalizer.normalize(pgPaymentId));
    }
}
