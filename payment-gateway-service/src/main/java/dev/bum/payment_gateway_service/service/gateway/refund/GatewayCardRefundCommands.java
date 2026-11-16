package dev.bum.payment_gateway_service.service.gateway.refund;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GatewayCardRefundCommands {

    // 가맹점에 속한 PG 원거래를 먼저 잠근다.
    private final GatewayPaymentJpaRepository payments;

    // 개별 요청의 최초 조건과 최종 결과를 보관한다.
    private final GatewayCardRefundJpaRepository refunds;

    // 금융 결과 반영과 같은 로컬 트랜잭션에 남기는 PG 이력.
    private final GatewayPaymentHistoryJpaRepository histories;

    // 승인 통보가 완료된 거래만 사용자 환불을 시작할 수 있다.
    private final GatewayCardNotificationJpaRepository notifications;

    /**
     * 원거래·소유자·요청 해시를 검증하고 최초 접수를 커밋한다. 카드사 HTTP는 수행하지 않는다.
     */
    public PaymentRefundResult prepare(
            AuthenticatedMerchant merchant,
            CardRefundCommand command,
            GatewayPaymentRefundRequest request
    ) {

        command.validate();

        if (merchant == null || merchant.merchantId() == null
                || (request.merchantId() != null && !request.merchantId().equals(merchant.merchantId()))) {
            throw new AccessDeniedException("환불 가맹점이 일치하지 않습니다.");
        }

        GatewayPayment payment = payments.findOwnedForUpdate(merchant.merchantId(), command.pgPaymentId())
                .orElseThrow(GatewayPaymentNotFoundException::new);

        if (payment.getPaymentMethod() != PaymentMethod.CREDIT_CARD
                || !payment.getBuyerReference().equals(request.buyerReference())) {
            throw new GatewayPaymentConflictException("환불 원거래의 수단 또는 소유자가 일치하지 않습니다.");
        }

        GatewayCardRefund existing = refunds.findByPaymentPgPaymentIdAndRefundRequestId(
                command.pgPaymentId(), command.refundRequestId()).orElse(null);

        if (existing != null) {
            existing.validate(command);

            return "REQUESTED".equals(existing.getStatus()) ? null : existing.result();
        }

        if (!notifications.findByPaymentPgPaymentId(command.pgPaymentId())
                .map(notification -> "DELIVERED".equals(notification.getStatus())).orElse(false)) {
            throw new GatewayPaymentConflictException("가맹점 승인 결과 반영 후 환불을 신청해주세요.");
        }

        if (payment.getPaidAt() == null || (payment.getStatus() != GatewayPaymentStatus.PAID
                && payment.getStatus() != GatewayPaymentStatus.PARTIALLY_REFUNDED)
                || payment.getAmount().subtract(payment.getRefundedAmount()).compareTo(command.amount()) < 0) {
            throw new GatewayPaymentConflictException("환불 가능한 PG 원거래 또는 금액이 아닙니다.");
        }

        if (!refunds.findByPaymentPgPaymentIdAndStatus(command.pgPaymentId(), "REQUESTED").isEmpty()) {
            throw new GatewayPaymentConflictException("기존 미완료 환불 신청을 먼저 확인하거나 철회해주세요.");
        }

        refunds.saveAndFlush(GatewayCardRefund.pending(payment, command));

        return null;
    }

    /**
     * 동일 금융 결과를 한 번만 결제 상태·누적 금액·이력에 반영한다. 통신 실패와 금융 실패를 혼동하지 않는다.
     */
    public PaymentRefundResult confirm(CardRefundCommand command, PaymentRefundResult result) {

        if (result == null || !result.matches(command)) {
            throw new FinancialProviderResponseException();
        }

        GatewayPayment payment = payments.findForUpdate(command.pgPaymentId()).orElseThrow(GatewayPaymentNotFoundException::new);
        GatewayCardRefund row = refunds.findByPaymentPgPaymentIdAndRefundRequestId(command.pgPaymentId(), command.refundRequestId())
                .orElseThrow();
        row.validate(command);

        if (!"REQUESTED".equals(row.getStatus())) {
            if (!row.result().equals(result)) {
                throw new FinancialProviderResponseException();
            }

            return row.result();
        }

        GatewayPaymentStatus before = payment.getStatus();

        if ("SUCCEEDED".equals(result.outcome())) {
            payment.refundCard(result.amount());
        }

        row.complete(result.outcome(), result.completedAt());
        histories.saveAndFlush(GatewayPaymentHistory.cardRefund(payment, result, before));
        refunds.flush();

        return row.result();
    }
}
