package dev.bum.payment_gateway_service.service.gateway.payment;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalGateRequest;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class GatewayPaymentMethodChangeCommands {

    private final GatewayPaymentJpaRepository payments;

    private final GatewayPaymentHistoryJpaRepository histories;

    private final GatewayCardNotificationJpaRepository notifications;

    public record Preparation(FinancialCardApprovalGateRequest source, GatewayCardMethodChangeResponse replay) { }

    // HTTP 전에 전환 조건을 커밋해 응답 유실에도 다른 은행·기한으로 재해석하지 않는다.
    public Preparation prepare(AuthenticatedMerchant merchant, String id, GatewayCardMethodChangeRequest request) {

        GatewayPayment payment = owned(merchant, id, request);
        GatewayPaymentTerms target = target(payment, request);
        GatewayPaymentHistory intent = history(id, "REQUESTED");

        if (intent != null && !intent.getRequestHash().equals(target.fingerprint())) {
            throw new GatewayPaymentConflictException("최초 결제수단 전환 조건과 다릅니다.");
        }

        if (payment.getPaymentMethod() == PaymentMethod.BANK_TRANSFER && history(id, "COMPLETED") != null) {
            return new Preparation(null, response(payment, false));
        }

        if (payment.getPaymentMethod() == PaymentMethod.CREDIT_CARD && payment.getStatus() == GatewayPaymentStatus.PAID) {
            return new Preparation(null, response(payment, true));
        }

        if (payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD || payment.getStatus() != GatewayPaymentStatus.READY) {
            throw new GatewayPaymentConflictException("결제수단을 바꿀 수 없는 PG 상태입니다.");
        }

        if (intent == null) {
            if (payment.getMerchant().getStatus() != dev.bum.payment_gateway_service.jpa.merchant.MerchantStatus.ACTIVE) {
                throw new AccessDeniedException("신규 결제수단 전환을 실행할 수 없는 가맹점입니다.");
            }
            if (!target.expiresAt().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("입금 기한이 만료되었습니다.");
            }

            intent = histories.saveAndFlush(GatewayPaymentHistory.methodChange(payment, target, "REQUESTED"));
        }

        LocalDateTime sourceExpires = LocalDateTime.parse((String) intent.getDetails().get("sourceExpiresAt"));

        return new Preparation(new FinancialCardApprovalGateRequest(id, payment.getBuyerReference(), payment.getAmount(), sourceExpires), null);
    }

    // 금융 차단이 확인된 뒤 수단·버전·완료 이력을 한 번만 변경한다.
    public GatewayCardMethodChangeResponse switched(AuthenticatedMerchant merchant, String id, GatewayCardMethodChangeRequest request) {

        GatewayPayment payment = owned(merchant, id, request);
        GatewayPaymentTerms target = target(payment, request);
        GatewayPaymentHistory intent = history(id, "REQUESTED");

        if (intent == null || !intent.getRequestHash().equals(target.fingerprint())) {
            throw new GatewayPaymentConflictException("결제수단 전환 요청이 일치하지 않습니다.");
        }

        if (history(id, "COMPLETED") == null) {
            if (histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(id, GatewayPaymentOperation.APPROVE, "INITIAL").isPresent()) {
                throw new GatewayPaymentConflictException("이미 승인된 카드는 수단을 바꿀 수 없습니다.");
            }

            payment.switchToBank(target.bankCompany(), target.expiresAt());
            histories.saveAndFlush(GatewayPaymentHistory.methodChange(payment, target, "COMPLETED"));
        }

        return response(payment, false);
    }

    public GatewayCardMethodChangeResponse approved(AuthenticatedMerchant merchant, String id, GatewayCardMethodChangeRequest request) {

        GatewayPayment payment = owned(merchant, id, request);

        if (payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD || payment.getStatus() != GatewayPaymentStatus.PAID) {
            throw new GatewayPaymentConflictException("카드 승인 복구 상태를 확인할 수 없습니다.");
        }

        if (history(id, "APPROVAL_FOUND") == null) {
            histories.saveAndFlush(GatewayPaymentHistory.methodChange(payment, target(payment, request), "APPROVAL_FOUND"));
        }

        return response(payment, true);
    }

    private GatewayPayment owned(AuthenticatedMerchant merchant, String id, GatewayCardMethodChangeRequest request) {

        if (merchant == null || merchant.merchantId() == null
                || (request.merchantId() != null && !request.merchantId().equals(merchant.merchantId()))) {
            throw new AccessDeniedException("인증된 가맹점이 일치하지 않습니다.");
        }

        GatewayPayment payment = payments.findOwnedForUpdate(merchant.merchantId(), id).orElseThrow(GatewayPaymentNotFoundException::new);

        if (!payment.getBuyerReference().equals(request.buyerReference()) || request.amount() == null
                || payment.getAmount().compareTo(request.amount()) != 0) {
            throw new GatewayPaymentConflictException("전환 요청의 원거래·구매자·금액이 일치하지 않습니다.");
        }

        return payment;
    }

    private GatewayPaymentTerms target(GatewayPayment payment, GatewayCardMethodChangeRequest request) {

        return new GatewayPaymentTerms(payment.getMerchantPaymentNo(), payment.getBuyerReference(), PaymentMethod.BANK_TRANSFER,
                request.amount(), request.expiresAt(), request.bankCompany());
    }

    private GatewayPaymentHistory history(String id, String key) {

        return histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(id, GatewayPaymentOperation.CHANGE_METHOD, key).orElse(null);
    }

    private GatewayCardMethodChangeResponse response(GatewayPayment payment, boolean approved) {

        return new GatewayCardMethodChangeResponse(approved ? "APPROVED" : "SWITCHED", payment.getPgPaymentId(),
                payment.getMerchant().getMerchantId(), payment.getMerchantPaymentNo(), payment.getBuyerReference(), payment.getPaymentMethod(),
                payment.getAmount(), payment.getBankCompany(), payment.getExpiresAt(), payment.getMethodRevision(),
                approved ? notifications.findByPaymentPgPaymentId(payment.getPgPaymentId()).orElseThrow().toNotification() : null);
    }
}
