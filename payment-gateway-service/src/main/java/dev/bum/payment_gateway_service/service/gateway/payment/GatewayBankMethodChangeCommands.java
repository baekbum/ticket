package dev.bum.payment_gateway_service.service.gateway.payment;

import dev.bum.common.service.paymentgateway.payment.*;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.MerchantStatus;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class GatewayBankMethodChangeCommands {

    private final GatewayPaymentJpaRepository payments;

    private final GatewayPaymentHistoryJpaRepository histories;

    public record Preparation(BankVirtualAccountIssueRequest source, GatewayBankMethodChangeResponse replay) { }

    public Preparation prepare(AuthenticatedMerchant merchant, String id, GatewayBankMethodChangeRequest request) {

        GatewayPayment payment = owned(merchant, id, request);
        GatewayPaymentTerms target = target(payment, request);
        GatewayPaymentHistory intent = history(id, "TO_CARD_REQUESTED");

        if (intent != null && !intent.getRequestHash().equals(target.fingerprint())) {
            throw new GatewayPaymentConflictException("최초 카드 전환 조건과 다릅니다.");
        }

        if (history(id, "TO_CARD_COMPLETED") != null) {
            return new Preparation(null, response(payment));
        }

        if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER || payment.getStatus() != GatewayPaymentStatus.READY
                || payment.getMethodRevision() != 0 || histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(
                        id, GatewayPaymentOperation.ISSUE_ACCOUNT, "INITIAL").isPresent()) {
            throw new GatewayPaymentConflictException("이미 발급되었거나 카드로 전환할 수 없는 거래입니다.");
        }

        if (intent == null) {
            if (payment.getMerchant().getStatus() != MerchantStatus.ACTIVE) {
                throw new AccessDeniedException("신규 전환이 불가능한 가맹점입니다.");
            }

            if (!target.expiresAt().isAfter(LocalDateTime.now())) {
                throw new GatewayPaymentConflictException("카드 결제 기한이 만료되었습니다.");
            }

            histories.saveAndFlush(GatewayPaymentHistory.methodChange(payment, target, "TO_CARD_REQUESTED"));
        }

        return new Preparation(new BankVirtualAccountIssueRequest(id, payment.getBankCompany(), payment.getAmount(), payment.getExpiresAt()), null);
    }

    public GatewayBankMethodChangeResponse switched(AuthenticatedMerchant merchant, String id, GatewayBankMethodChangeRequest request) {

        GatewayPayment payment = owned(merchant, id, request);
        GatewayPaymentTerms target = target(payment, request);
        GatewayPaymentHistory intent = history(id, "TO_CARD_REQUESTED");

        if (intent == null || !intent.getRequestHash().equals(target.fingerprint())) {
            throw new GatewayPaymentConflictException("최초 카드 전환 조건과 다릅니다.");
        }

        if (history(id, "TO_CARD_COMPLETED") == null) {
            payment.switchToCard(target.expiresAt());
            histories.saveAndFlush(GatewayPaymentHistory.methodChange(payment, target, "TO_CARD_COMPLETED"));
        }

        return response(payment);
    }

    private GatewayPayment owned(AuthenticatedMerchant merchant, String id, GatewayBankMethodChangeRequest request) {

        if (merchant == null || merchant.merchantId() == null
                || (request.merchantId() != null && !request.merchantId().equals(merchant.merchantId()))) {
            throw new AccessDeniedException("인증된 가맹점이 일치하지 않습니다.");
        }

        GatewayPayment payment = payments.findOwnedForUpdate(merchant.merchantId(), id).orElseThrow(GatewayPaymentNotFoundException::new);

        if (!payment.getBuyerReference().equals(request.buyerReference()) || request.amount() == null
                || payment.getAmount().compareTo(request.amount()) != 0) {
            throw new GatewayPaymentConflictException("전환 요청의 원거래 조건이 일치하지 않습니다.");
        }

        return payment;
    }

    private GatewayPaymentTerms target(GatewayPayment payment, GatewayBankMethodChangeRequest request) {

        return new GatewayPaymentTerms(payment.getMerchantPaymentNo(), payment.getBuyerReference(), PaymentMethod.CREDIT_CARD,
                request.amount(), request.expiresAt(), null);
    }

    private GatewayPaymentHistory history(String id, String key) {

        return histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(id, GatewayPaymentOperation.CHANGE_METHOD, key).orElse(null);
    }

    private GatewayBankMethodChangeResponse response(GatewayPayment payment) {

        return new GatewayBankMethodChangeResponse(payment.getPgPaymentId(), payment.getMerchant().getMerchantId(),
                payment.getMerchantPaymentNo(), payment.getBuyerReference(), payment.getPaymentMethod(),
                payment.getAmount(), payment.getExpiresAt(), payment.getMethodRevision());
    }
}
