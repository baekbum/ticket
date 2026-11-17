package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.paymentgateway.notification.BankDepositNotification;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountCancelOutboxService;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentLockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@Transactional
public class VirtualAccountIssuanceCheckoutCommandService {

    // 취소·입금과 같은 예약 → 결제 순서로 잠근다.
    private final VirtualAccountPaymentLockService locks;
    // 발급 중 취소된 결제의 은행 취소 요청을 같은 커밋에 남긴다.
    private final VirtualAccountCancelOutboxService cancellations;
    // 요청 본문이 아닌 서버 설정에서 결정한 Ticksy 가맹점 코드.
    private final long merchantId;

    /**
     * 로컬 결제 처리와 등록 가맹점 설정을 연결한다.
     */
    public VirtualAccountIssuanceCheckoutCommandService(
            VirtualAccountPaymentLockService locks,
            VirtualAccountCancelOutboxService cancellations,
            @Value("${app.payment-gateway.merchant-id:1}") long merchantId
    ) {

        if (merchantId <= 0) {
            throw new IllegalArgumentException("PG 가맹점 코드가 필요합니다.");
        }

        this.locks = locks;
        this.cancellations = cancellations;
        this.merchantId = merchantId;
    }

    /**
     * 최초 저장한 불변 요청과 현재 연결 상태만 짧은 트랜잭션으로 읽는다.
     */
    public VirtualAccountIssuePreparation findPreparation(String paymentNo) {

        Payment payment = locks.lock(paymentNo);

        if (payment.getGatewayBankCompany() == null) {
            return new VirtualAccountIssuePreparation(null, null, false, payment.toResponse());
        }

        GatewayPaymentPrepareRequest request = new GatewayPaymentPrepareRequest(merchantId, paymentNo,
                payment.getReservation().getUserId(), PaymentMethod.BANK_TRANSFER,
                BigDecimal.valueOf(payment.getAmount()), payment.getGatewayExpiresAt(), payment.getGatewayBankCompany());

        return new VirtualAccountIssuePreparation(request, payment.getPgPaymentId(),
                payment.isGatewayIssuePending(), payment.toResponse());
    }

    /**
     * PG 준비 응답이 최초 조건과 일치할 때만 식별자를 저장한다. 이후 발급은 이 저장이 커밋된 뒤 실행한다.
     */
    public String prepared(String paymentNo, GatewayPaymentPrepareResponse response) {

        Payment payment = locks.lock(paymentNo);

        if (response == null || !Long.valueOf(merchantId).equals(response.merchantId())
                || !paymentNo.equals(response.paymentNo())
                || !payment.getReservation().getUserId().equals(response.buyerReference())
                || response.paymentMethod() != PaymentMethod.BANK_TRANSFER
                || response.bankCompany() != payment.getGatewayBankCompany()
                || response.amount() == null || BigDecimal.valueOf(payment.getAmount()).compareTo(response.amount()) != 0
                || !Objects.equals(payment.getGatewayExpiresAt(), response.expiresAt()) || response.status() == null) {
            throw new IllegalStateException("PG 준비 결과가 최초 결제 조건과 일치하지 않습니다.");
        }

        payment.bindGatewayPayment(response.pgPaymentId());

        return payment.getPgPaymentId();
    }

    /**
     * 발급 응답을 반영하고, 그 사이 취소된 예매는 되살리지 않고 은행 취소를 이어간다.
     */
    public PaymentResponse issued(String paymentNo, GatewayAccountIssueResponse response) {

        Payment payment = locks.lock(paymentNo);

        if (response == null || !Long.valueOf(merchantId).equals(response.merchantId())
                || !Objects.equals(payment.getPgPaymentId(), response.pgPaymentId())
                || !paymentNo.equals(response.paymentNo())
                || !payment.getReservation().getUserId().equals(response.buyerReference())
                || response.bankCompany() != payment.getGatewayBankCompany()
                || response.amount() == null || BigDecimal.valueOf(payment.getAmount()).compareTo(response.amount()) != 0
                || !"WAITING_DEPOSIT".equals(response.bankStatus()) || response.pgStatus() == null) {
            throw new IllegalStateException("PG 발급 결과가 최초 결제 조건과 일치하지 않습니다.");
        }

        payment.bindGatewayAccount(response.bankName(), response.accountNumber(), response.expiresAt());
        enqueueCancelled(payment);

        return payment.toResponse();
    }

    /**
     * 발급 HTTP 응답보다 입금 통보가 먼저 도착하면 저장한 PG 식별자·원거래 조건으로 계좌를 복구한다.
     */
    public void bindDeposit(BankDepositNotification event) {

        Payment payment = locks.lock(event.merchantPaymentNo());

        if (!payment.getReservation().getUserId().equals(event.buyerReference())) {
            throw new org.springframework.security.access.AccessDeniedException("입금 통보의 예매 소유자가 일치하지 않습니다.");
        }

        if (!Long.valueOf(merchantId).equals(event.merchantId())
                || !Objects.equals(payment.getPgPaymentId(), event.pgPaymentId())
                || payment.getGatewayBankCompany() != event.bankCompany()
                || !payment.getReservation().getUserId().equals(event.buyerReference())
                || BigDecimal.valueOf(payment.getAmount()).compareTo(event.amount()) != 0) {
            throw new IllegalArgumentException("입금 통보가 저장한 PG 원거래와 일치하지 않습니다.");
        }

        payment.bindGatewayAccount(event.bankCompany().getBankName(), event.accountNumber(), payment.getGatewayExpiresAt());
        enqueueCancelled(payment);
    }

    /**
     * 취소와 발급이 경합한 경우 발급 계좌와 취소 의도를 같은 트랜잭션에 보존한다.
     */
    private void enqueueCancelled(Payment payment) {

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            cancellations.enqueue(payment);
        }
    }
}
