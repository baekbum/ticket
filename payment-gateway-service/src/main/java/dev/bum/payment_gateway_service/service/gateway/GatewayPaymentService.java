package dev.bum.payment_gateway_service.service.gateway;

import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentNotFoundException;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

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

        String normalizedPaymentId = normalizePaymentId(paymentId);

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

        if (merchant == null || merchant.merchantId() == null || merchant.merchantId() <= 0
                || merchant.credentialKey() == null || merchant.credentialKey().isBlank()) {
            throw new AccessDeniedException("가맹점 인증 정보가 필요합니다.");
        }
    }

    /**
     * 날짜 접두사와 UUID를 검증하고 대소문자를 정규화한다. 기존 36자 UUID도 조회할 수 있다.
     */
    private String normalizePaymentId(String paymentId) {

        if (paymentId == null || (paymentId.length() != 36 && paymentId.length() != 44)) {
            throw new IllegalArgumentException("PG 결제 식별자 형식이 올바르지 않습니다.");
        }

        if (paymentId.length() == 36) {
            return UUID.fromString(paymentId).toString();
        }

        String issuedDate = paymentId.substring(0, 8);

        try {
            LocalDate.parse(issuedDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("PG 결제 식별자의 발급일이 올바르지 않습니다.", exception);
        }

        return issuedDate + UUID.fromString(paymentId.substring(8)).toString();
    }
}
