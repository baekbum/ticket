package dev.bum.payment_gateway_service.service.gateway.payment;

import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentNotFoundException;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.support.GatewayPaymentIdNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GatewayPaymentService {

    // 별도 프록시에서 준비·이력 저장과 충돌 후 재조회를 분리한다.
    private final GatewayPaymentCommandService commands;
    // 모든 외부 조회에 인증된 가맹점 범위를 포함한다.
    private final GatewayPaymentJpaRepository payments;

    /**
     * 본문 가맹점 코드의 위변조를 거절하고 멱등 준비를 조정한다.
     */
    public GatewayPaymentResponse prepare(AuthenticatedMerchant merchant, GatewayPaymentPrepareRequest request) {

        requirePrincipal(merchant);

        if (request.merchantId() != null && !request.merchantId().equals(merchant.merchantId())) {
            throw new AccessDeniedException("인증된 가맹점과 요청 가맹점이 일치하지 않습니다.");
        }

        GatewayPaymentTerms terms = request.toTerms();

        try {
            return commands.prepare(merchant, terms);
        } catch (DataIntegrityViolationException conflict) {
            return commands.replayCommitted(merchant, terms).orElseThrow(() -> conflict);
        }
    }

    /**
     * 존재하지 않는 거래와 다른 가맹점 거래를 동일한 404로 처리한다.
     */
    @Transactional(readOnly = true)
    public GatewayPaymentResponse find(AuthenticatedMerchant merchant, String paymentId) {

        requirePrincipal(merchant);

        String normalizedPaymentId = GatewayPaymentIdNormalizer.normalize(paymentId);

        return payments.findByPgPaymentIdAndMerchantMerchantId(normalizedPaymentId, merchant.merchantId())
                .map(GatewayPaymentResponse::from).orElseThrow(GatewayPaymentNotFoundException::new);
    }

    /**
     * 가맹점 내부 번호는 반드시 인증된 merchant와 함께 조회한다.
     */
    @Transactional(readOnly = true)
    public GatewayPaymentResponse findByPaymentNo(AuthenticatedMerchant merchant, String paymentNo) {

        requirePrincipal(merchant);

        return payments.findByMerchantMerchantIdAndMerchantPaymentNo(merchant.merchantId(), paymentNo)
                .map(GatewayPaymentResponse::from).orElseThrow(GatewayPaymentNotFoundException::new);
    }

    /**
     * 서비스 직접 호출에서도 인증 주체 없는 접근을 거절한다.
     */
    private void requirePrincipal(AuthenticatedMerchant merchant) {

        if (merchant == null || merchant.merchantId() == null || merchant.merchantId() <= 0) {
            throw new AccessDeniedException("가맹점 인증 정보가 필요합니다.");
        }
    }
}
