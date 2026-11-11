package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.service.paymentgateway.notification.BankCancellationNotification;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GatewayVirtualAccountCancelCommands {

    // 요청 본문이 아닌 현재 자격증명과 가맹점 연결을 검증한다.
    private final MerchantJpaRepository merchants;
    // 소유 범위 검증과 상태 확정 때만 PG 거래를 잠근다.
    private final GatewayPaymentJpaRepository payments;
    // 발급 스냅샷과 전체 취소 성공/거절 이력.
    private final GatewayPaymentHistoryJpaRepository histories;
    // PG→은행 요청과 PG→가맹점 결과는 서로 다른 전달 행이다.
    private final BankTransferOutboxJpaRepository events;
    // 최초 입력과 최종 결과의 JSON 스냅샷 변환.
    private final ObjectMapper json;

    /**
     * 소유 거래와 요청을 검증하고 취소를 한 번 접수한다. 금융 취소 가능 여부는 은행이 판단한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayVirtualAccountCancellationResponse enqueue(
            AuthenticatedMerchant principal,
            String id,
            GatewayVirtualAccountPaymentCancelRequest request
    ) {

        requireMerchant(principal, request.merchantId());
        GatewayPayment payment = ownedPayment(principal, id);
        validateRequest(payment, request);
        BankVirtualAccountCancelRequest bank = new BankVirtualAccountCancelRequest(id, request.bankCompany(),
                request.accountNumber(), request.amount());
        Optional<BankTransferOutbox> previous = events.findByFlowAndPgPaymentId(
                BankTransferOutbox.Flow.BANK_CANCEL_REQUEST, id);

        if (previous.isPresent()) {
            GatewayBankCancellationInput original = json.convertValue(previous.get().getPayload(), GatewayBankCancellationInput.class);

            if (!original.bankRequest().equals(bank)) {
                throw new GatewayPaymentConflictException("같은 취소 키의 최초 요청 내용과 일치하지 않습니다.");
            }

            return response(payment, previous.get());
        }

        Merchant merchant = payment.getMerchant();
        GatewayBankCancellationInput input = new GatewayBankCancellationInput(bank, merchant.getMerchantId(),
                payment.getMerchantPaymentNo(), payment.getBuyerReference(), merchant.getNotificationContract(),
                merchant.getNotificationTargetKey());
        BankTransferOutbox event = events.saveAndFlush(BankTransferOutbox.pending(
                BankTransferOutbox.Flow.BANK_CANCEL_REQUEST, id + ":CANCEL:INITIAL", id, map(input)));

        return response(payment, event);
    }

    /**
     * 인증된 가맹점만 취소 접수 상태와 최초 확정 결과를 조회한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public GatewayVirtualAccountCancellationResponse find(AuthenticatedMerchant principal, String id) {

        requireMerchant(principal, null);
        GatewayPayment payment = payments.findByPgPaymentIdAndMerchantMerchantId(id, principal.merchantId())
                .orElseThrow(GatewayPaymentNotFoundException::new);
        BankTransferOutbox event = events.findByFlowAndPgPaymentId(BankTransferOutbox.Flow.BANK_CANCEL_REQUEST, id)
                .orElseThrow(GatewayPaymentNotFoundException::new);

        return response(payment, event);
    }

    /**
     * 은행 결과·요청 전달 완료·가맹점 결과 Outbox를 함께 커밋한다. 입금 처리는 호출하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(BankTransferDelivery delivery, BankVirtualAccountCancellationResult result) {

        GatewayPayment payment = payments.findForUpdate(delivery.pgPaymentId()).orElseThrow(GatewayPaymentNotFoundException::new);
        BankTransferOutbox event = events.findForUpdate(delivery.outboxId()).orElseThrow();

        if (!event.owns(delivery)) {
            return;
        }

        GatewayBankCancellationInput input = json.convertValue(event.getPayload(), GatewayBankCancellationInput.class);
        validateResult(payment, input.bankRequest(), result);
        GatewayPaymentStatus before = payment.getStatus();

        if ("CANCELLED".equals(result.outcome())) {
            payment.confirmBankCancellation();

            if (histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(payment.getPgPaymentId(),
                    GatewayPaymentOperation.CANCEL, "INITIAL").isEmpty()) {
                histories.saveAndFlush(GatewayPaymentHistory.virtualAccountCancelled(payment, result.account(), before));
            }
        } else {
            histories.saveAndFlush(GatewayPaymentHistory.virtualAccountCancellationRejected(payment, result.reason()));
        }

        BankCancellationNotification notification = new BankCancellationNotification(1, event.getEventId(),
                input.merchantId(), payment.getPgPaymentId(), input.merchantPaymentNo(), input.buyerReference(),
                PaymentMethod.BANK_TRANSFER, input.bankRequest().amount(), input.bankRequest().accountNumber(),
                input.bankRequest().bankCompany(), result.outcome(), result.reason(),
                "CANCELLED".equals(result.outcome()) ? payment.getCancelledAt() : null, false);
        events.saveAndFlush(BankTransferOutbox.pending(BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT,
                notification.eventId(), payment.getPgPaymentId(), map(new GatewayBankCancellationNotification(notification,
                        input.notificationContract(), input.notificationTargetKey()))));
        event.delivered(map(result));
        events.saveAndFlush(event);
    }

    /**
     * 본문 가맹점 코드와 현재 자격증명 연결을 확인한다. 사용 중단 후에도 기존 거래 정리는 허용한다.
     */
    private void requireMerchant(AuthenticatedMerchant principal, Long merchantId) {

        if (principal == null || principal.merchantId() == null || principal.credentialKey() == null
                || (merchantId != null && !merchantId.equals(principal.merchantId()))) {
            throw new AccessDeniedException("인증된 가맹점과 요청 가맹점이 일치하지 않습니다.");
        }

        merchants.findById(principal.merchantId()).filter(merchant -> merchant.getCredentialKey().equals(principal.credentialKey()))
                .orElseThrow(() -> new AccessDeniedException("가맹점 인증 정보가 변경되었습니다."));
    }

    /**
     * 없는 거래와 다른 가맹점 거래에 같은 404를 반환한다.
     */
    private GatewayPayment ownedPayment(AuthenticatedMerchant principal, String id) {

        return payments.findOwnedForUpdate(principal.merchantId(), id).orElseThrow(GatewayPaymentNotFoundException::new);
    }

    /**
     * 원거래의 수단·소유자·금액·은행·발급 계좌를 검증한다. 입금 여부는 판단하지 않는다.
     */
    private void validateRequest(GatewayPayment payment, GatewayVirtualAccountPaymentCancelRequest request) {

        if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || !payment.getBuyerReference().equals(request.buyerReference()) || request.amount() == null
                || payment.getAmount().compareTo(request.amount()) != 0 || payment.getBankCompany() != request.bankCompany()) {
            throw new GatewayPaymentConflictException("계좌 취소 요청이 PG 원거래와 일치하지 않습니다.");
        }

        histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(payment.getPgPaymentId(),
                GatewayPaymentOperation.ISSUE_ACCOUNT, "INITIAL").ifPresent(history -> {
                    if (!request.accountNumber().equals(history.getDetails().get("accountNumber"))) {
                        throw new GatewayPaymentConflictException("취소 요청 계좌가 최초 발급 계좌와 일치하지 않습니다.");
                    }
                });
    }

    /**
     * 은행의 확정 결과와 원거래 연결을 검증한다. 과거 입금은 취소 스냅샷으로만 보존한다.
     */
    private void validateResult(
            GatewayPayment payment,
            BankVirtualAccountCancelRequest request,
            BankVirtualAccountCancellationResult result
    ) {

        if (result == null || !result.matches(request)) {
            throw new FinancialProviderResponseException();
        }

        BankVirtualAccountCancelResponse bank = result.account();

        if (bank == null) {
            if ("REJECTED".equals(result.outcome()) && "ACCOUNT_NOT_FOUND".equals(result.reason())) {
                return;
            }

            throw new FinancialProviderResponseException();
        }

        if (!payment.getPgPaymentId().equals(bank.pgPaymentId()) || !request.accountNumber().equals(bank.accountNumber())
                || request.bankCompany() != bank.bankCompany() || bank.amount() == null
                || request.amount().compareTo(bank.amount()) != 0 || !payment.getExpiresAt().equals(bank.expiresAt())
                || !request.bankCompany().getBankName().equals(bank.bankName())
                || ("CANCELLED".equals(result.outcome()) && bank.status() != VirtualAccountPaymentStatus.CANCELLED)) {
            throw new FinancialProviderResponseException();
        }
    }

    /**
     * 동일 요청의 접수/결과를 최초 저장 내용으로 재구성한다.
     */
    private GatewayVirtualAccountCancellationResponse response(GatewayPayment payment, BankTransferOutbox event) {

        BankVirtualAccountCancellationResult result = event.getResult() == null ? null
                : json.convertValue(event.getResult(), BankVirtualAccountCancellationResult.class);

        return new GatewayVirtualAccountCancellationResponse(payment.getPgPaymentId(), payment.getMerchantPaymentNo(),
                event.getEventId(), result == null ? "PROCESSING" : result.outcome(), result == null ? null : result.reason(),
                payment.getStatus(), result != null && "CANCELLED".equals(result.outcome()) ? payment.getCancelledAt() : null, false);
    }

    /**
     * 토큰 없이 허용된 DTO 내용만 JSON 객체로 고정한다.
     */
    private Map<String, Object> map(Object value) {

        return json.convertValue(value, new TypeReference<>() { });
    }
}
