package dev.bum.payment_gateway_service.service.gateway.payment;

import dev.bum.payment_gateway_service.dto.gateway.GatewayPaymentResponse;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GatewayPaymentCommandService {

    // 실행 시점의 가맹점 등록·사용 상태를 재검증한다.
    private final MerchantJpaRepository merchants;
    // 가맹점 결제번호와 PG 거래를 같은 행으로 매핑한다.
    private final GatewayPaymentJpaRepository payments;
    // PG 최초 등록과 이력을 함께 커밋한다.
    private final GatewayPaymentHistoryJpaRepository histories;

    /**
     * 금융 처리를 실행하지 않고 PG 거래와 최초 이력을 원자적으로 준비한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayPaymentResponse prepare(AuthenticatedMerchant principal, GatewayPaymentTerms terms) {

        Merchant merchant = merchants.findById(principal.merchantId())
                .filter(found -> found.getCredentialKey().equals(principal.credentialKey()))
                .orElseThrow(() -> new AccessDeniedException("가맹점 인증 정보가 변경되었습니다."));

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new AccessDeniedException("신규 결제를 준비할 수 없는 가맹점입니다.");
        }

        Optional<GatewayPayment> existing = payments.findByMerchantMerchantIdAndMerchantPaymentNo(
                merchant.getMerchantId(), terms.paymentNo());

        if (existing.isPresent()) return replay(existing.get(), terms);

        if (!terms.expiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("결제 기한이 만료되었습니다.");
        }

        GatewayPayment payment = payments.saveAndFlush(GatewayPayment.prepare(merchant, terms));
        histories.saveAndFlush(GatewayPaymentHistory.registered(payment));

        return GatewayPaymentResponse.from(payment);
    }

    /**
     * 유니크 충돌의 트랜잭션을 완전히 롤백한 뒤 승자가 저장한 내용을 확인한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<GatewayPaymentResponse> replayCommitted(AuthenticatedMerchant merchant, GatewayPaymentTerms terms) {

        Merchant current = merchants.findById(merchant.merchantId())
                .filter(found -> found.getCredentialKey().equals(merchant.credentialKey()))
                .orElseThrow(() -> new AccessDeniedException("가맹점 인증 정보가 변경되었습니다."));

        if (current.getStatus() != MerchantStatus.ACTIVE) {
            throw new AccessDeniedException("신규 결제를 준비할 수 없는 가맹점입니다.");
        }

        return payments.findByMerchantMerchantIdAndMerchantPaymentNo(merchant.merchantId(), terms.paymentNo())
                .map(payment -> replay(payment, terms));
    }

    /**
     * 최초 금액·예매자·수단·기한을 비교한 뒤 동일 요청만 재응답한다.
     */
    private GatewayPaymentResponse replay(GatewayPayment payment, GatewayPaymentTerms terms) {

        if (!payment.matches(terms)) throw new GatewayPaymentConflictException();

        return GatewayPaymentResponse.from(payment);
    }
}
