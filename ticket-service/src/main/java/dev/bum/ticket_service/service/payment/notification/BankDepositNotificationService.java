package dev.bum.ticket_service.service.payment.notification;

import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.service.payment.payment.PaymentService;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class BankDepositNotificationService {

    // 기존 계좌/은행/금액 검증·입금 완료·취소 예매·중복 통보 처리를 재사용한다.
    private final PaymentService payments;
    // 발급 응답보다 통보가 먼저 도착해도 저장한 PG 원거래로 계좌를 연결한다.
    private final VirtualAccountIssuanceCheckoutCommandService accounts;
    // 이 수신 서버에 PG가 발급한 가맹점 코드.
    private final long merchantId;

    /**
     * 기존 로컬 결제 처리와 서버의 가맹점 코드를 주입한다.
     */
    public BankDepositNotificationService(
            PaymentService payments,
            VirtualAccountIssuanceCheckoutCommandService accounts,
            @Value("${app.payment-gateway.merchant-id:1}") long merchantId
    ) {

        if (merchantId <= 0) {
            throw new IllegalArgumentException("PG 가맹점 코드 설정이 필요합니다.");
        }

        this.payments = payments;
        this.accounts = accounts;
        this.merchantId = merchantId;
    }

    /**
     * 공통 입금 통보를 Ticksy 기존 계약으로 변환하고 같은 이벤트의 결과를 반환한다.
     */
    @Transactional
    public BankDepositNotificationResponse receive(BankDepositNotification event) {

        event.validateContract();

        if (!Long.valueOf(merchantId).equals(event.merchantId())) {
            throw new AccessDeniedException("이 가맹점에 대한 PG 통보가 아닙니다.");
        }

        accounts.bindDeposit(event);

        VirtualAccountDepositCompleteRequest request = VirtualAccountDepositCompleteRequest.builder()
                .paymentNo(event.merchantPaymentNo()).bankCompany(event.bankCompany())
                .bankName(event.bankCompany().getBankName()).accountNumber(event.accountNumber())
                .depositorName(event.depositorName()).amount(event.amount()).depositedAt(event.depositedAt()).build();
        PaymentResponse payment = payments.completeVirtualAccountDepositFromGateway(request, event.buyerReference());

        if (payment == null || !event.pgPaymentId().equals(payment.getPgPaymentId())
                || !event.merchantPaymentNo().equals(payment.getPaymentNo())
                || payment.getMethod() != PaymentMethod.BANK_TRANSFER || payment.getAmount() == null
                || event.amount().compareTo(BigDecimal.valueOf(payment.getAmount())) != 0
                || !event.accountNumber().equals(payment.getAccountNumber())
                || !event.bankCompany().getBankName().equals(payment.getBankName())) {
            throw new IllegalStateException("입금 통보의 로컬 처리 결과가 원거래와 일치하지 않습니다.");
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return BankDepositNotificationResponse.of(event, BankDepositNotificationResponse.Outcome.RESERVATION_CANCELLED);
        }

        if ((payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED
                && payment.getStatus() != PaymentStatus.REFUNDED)
                || !event.depositorName().equals(payment.getDepositorName())
                || !event.depositedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).equals(payment.getPaidAt())) {
            throw new IllegalStateException("입금 통보의 업무 반영을 확인할 수 없습니다.");
        }

        return BankDepositNotificationResponse.of(event, BankDepositNotificationResponse.Outcome.APPLIED);
    }
}
