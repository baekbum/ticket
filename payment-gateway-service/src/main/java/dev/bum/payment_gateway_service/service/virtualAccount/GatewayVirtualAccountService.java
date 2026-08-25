package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundResponse;
import dev.bum.payment_gateway_service.exception.TicketVirtualAccountIssueException;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GatewayVirtualAccountService {

    private static final int MAX_ACCOUNT_ISSUE_ATTEMPTS = 5;

    private final DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;
    private final DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;
    private final TicketPaymentClient ticketPaymentClient;

    public GatewayVirtualAccountIssueResponse issue(GatewayVirtualAccountIssueRequest request) {
        return dummyVirtualAccountJpaRepository.findByPaymentNo(request.getPaymentNo())
                .map(virtualAccount -> toIssueResponse(virtualAccount, "이미 발급된 가상계좌입니다."))
                .orElseGet(() -> issueNewVirtualAccount(request));
    }

    public GatewayVirtualAccountDepositResponse deposit(GatewayVirtualAccountDepositRequest request) {
        DummyVirtualAccount virtualAccount = dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("가상계좌 정보를 찾을 수 없습니다."));

        validateDeposit(virtualAccount, request);
        virtualAccount.deposit(request.getDepositorName(), request.getDepositedAt());
        dummyVirtualAccountPaymentHistoryJpaRepository.save(DummyVirtualAccountPaymentHistory.deposited(virtualAccount));
        boolean ticketPaymentCompleted = applyTicketVirtualAccountDepositCompleted(virtualAccount);

        String message = ticketPaymentCompleted
                ? "가상계좌 입금과 티켓 결제 완료 반영이 완료되었습니다."
                : "가상계좌 입금은 완료되었지만 ticket-service 결제 완료 반영에 실패했습니다.";
        return toDepositResponse(virtualAccount, message);
    }

    public GatewayVirtualAccountRefundResponse refund(GatewayVirtualAccountRefundRequest request) {
        try {
            DummyVirtualAccount virtualAccount = dummyVirtualAccountJpaRepository.findByPaymentNo(request.getPaymentNo())
                    .orElseThrow(() -> new IllegalArgumentException("가상계좌 정보를 찾을 수 없습니다."));

            validateRefund(virtualAccount, request);
            log.info(
                    "무통장 환불 성공: paymentNo={}, refundBank={}, refundAccount={}, refundAccountHolder={}, refundAmount={}",
                    request.getPaymentNo(),
                    request.getRefundBankCompany(),
                    request.getRefundAccountNumber(),
                    request.getRefundAccountHolder(),
                    request.getRefundAmount()
            );

            return GatewayVirtualAccountRefundResponse.builder()
                    .paymentNo(request.getPaymentNo())
                    .refundBankCompany(request.getRefundBankCompany())
                    .refundAccountNumber(request.getRefundAccountNumber())
                    .refundAccountHolder(request.getRefundAccountHolder())
                    .refundedAmount(request.getRefundAmount())
                    .message("무통장 환불 입금이 완료되었습니다.")
                    .build();
        } catch (RuntimeException e) {
            log.warn(
                    "무통장 환불 실패: paymentNo={}, refundBank={}, refundAccount={}, refundAccountHolder={}, refundAmount={}, reason={}",
                    request.getPaymentNo(),
                    request.getRefundBankCompany(),
                    request.getRefundAccountNumber(),
                    request.getRefundAccountHolder(),
                    request.getRefundAmount(),
                    e.getMessage()
            );
            throw e;
        }
    }

    private GatewayVirtualAccountIssueResponse issueNewVirtualAccount(GatewayVirtualAccountIssueRequest request) {
        LocalDateTime expiresAt = resolveExpiresAt(request.getEventDateTime());
        String accountNumber = issueUniqueAccountNumber(request.getBankCompany());

        DummyVirtualAccount virtualAccount = dummyVirtualAccountJpaRepository.save(
                DummyVirtualAccount.issue(
                        request.getPaymentNo(),
                        request.getBankCompany(),
                        accountNumber,
                        request.getAmount(),
                        expiresAt
                )
        );
        dummyVirtualAccountPaymentHistoryJpaRepository.save(DummyVirtualAccountPaymentHistory.issued(virtualAccount));
        if (request.isTicketPaymentApplyRequired()) {
            applyTicketVirtualAccountIssued(virtualAccount);
        }

        return toIssueResponse(virtualAccount, "가상계좌가 발급되었습니다.");
    }

    private PaymentResponse applyTicketVirtualAccountIssued(DummyVirtualAccount virtualAccount) {
        try {
            return ticketPaymentClient.applyVirtualAccountIssued(
                    VirtualAccountIssuedRequest.builder()
                            .paymentNo(virtualAccount.getPaymentNo())
                            .amount(virtualAccount.getAmount())
                            .bankName(virtualAccount.getBankName())
                            .accountNumber(virtualAccount.getAccountNumber())
                            .expiresAt(virtualAccount.getExpiresAt())
                            .build()
            );
        } catch (RuntimeException e) {
            throw new TicketVirtualAccountIssueException("ticket-service 가상계좌 발급 정보 반영에 실패했습니다.", e);
        }
    }

    private boolean applyTicketVirtualAccountDepositCompleted(DummyVirtualAccount virtualAccount) {
        try {
            ticketPaymentClient.completeVirtualAccountDeposit(
                    VirtualAccountDepositCompleteRequest.builder()
                            .paymentNo(virtualAccount.getPaymentNo())
                            .bankCompany(virtualAccount.getBankCompany())
                            .bankName(virtualAccount.getBankName())
                            .accountNumber(virtualAccount.getAccountNumber())
                            .depositorName(virtualAccount.getDepositorName())
                            .amount(virtualAccount.getAmount())
                            .depositedAt(virtualAccount.getDepositedAt())
                            .build()
            );

            virtualAccount.completeTicketPayment(null);

            dummyVirtualAccountPaymentHistoryJpaRepository.save(
                    DummyVirtualAccountPaymentHistory.ticketPaymentCompleted(virtualAccount)
            );

            return true;
        } catch (RuntimeException e) {
            virtualAccount.failTicketPayment("ticket-service 입금 완료 반영 실패: " + e.getMessage());

            dummyVirtualAccountPaymentHistoryJpaRepository.save(
                    DummyVirtualAccountPaymentHistory.ticketPaymentFailed(virtualAccount)
            );

            return false;
        }
    }

    /**
     * 공연 당일에는 무통장 입금 결제 불가
     * 공연 하루 전날이라면 금일 23시 59시 59초까지 무통장 결제 가능
     * 이외에는 다음날 23시 59분 59초까지 가능
     * @param eventDateTime
     * @return
     */
    private LocalDateTime resolveExpiresAt(LocalDateTime eventDateTime) {
        LocalDate today = LocalDate.now();
        LocalDate eventDate = eventDateTime.toLocalDate();

        if (!today.isBefore(eventDate)) {
            throw new IllegalArgumentException("공연 당일에는 무통장 입금을 사용할 수 없습니다.");
        }

        LocalDate tomorrow = today.plusDays(1);
        LocalDate expirationDate = tomorrow.isBefore(eventDate) ? tomorrow : today;
        return LocalDateTime.of(expirationDate, LocalTime.of(23, 59, 59));
    }

    private String issueUniqueAccountNumber(BankCompany bankCompany) {
        for (int attempt = 0; attempt < MAX_ACCOUNT_ISSUE_ATTEMPTS; attempt++) {
            String accountNumber = generateAccountNumber(bankCompany);
            if (!dummyVirtualAccountJpaRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }

        throw new IllegalStateException("가상계좌 번호를 발급하지 못했습니다.");
    }

    private String generateAccountNumber(BankCompany bankCompany) {
        int middle = ThreadLocalRandom.current().nextInt(1000, 10000);
        int last = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return bankCompany.getAccountPrefix() + "-" + middle + "-" + last;
    }

    private void validateDeposit(DummyVirtualAccount virtualAccount, GatewayVirtualAccountDepositRequest request) {
        if (virtualAccount.getStatus() == VirtualAccountPaymentStatus.DEPOSITED
                || virtualAccount.getStatus() == VirtualAccountPaymentStatus.TICKET_PAYMENT_COMPLETED
                || virtualAccount.getStatus() == VirtualAccountPaymentStatus.TICKET_PAYMENT_FAILED) {
            throw new IllegalArgumentException("이미 입금 처리된 가상계좌입니다.");
        }
        if (virtualAccount.getStatus() != VirtualAccountPaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("입금 처리할 수 없는 가상계좌 상태입니다.");
        }
        LocalDateTime depositedAt = request.getDepositedAt() != null ? request.getDepositedAt() : LocalDateTime.now();
        if (depositedAt.isAfter(virtualAccount.getExpiresAt())) {
            virtualAccount.expire();
            throw new IllegalArgumentException("입금 기한이 만료되었습니다.");
        }
        if (virtualAccount.getAmount().compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("입금 금액이 일치하지 않습니다.");
        }
    }

    private void validateRefund(DummyVirtualAccount virtualAccount, GatewayVirtualAccountRefundRequest request) {
        if (virtualAccount.getStatus() != VirtualAccountPaymentStatus.TICKET_PAYMENT_COMPLETED) {
            throw new IllegalArgumentException("환불할 수 없는 가상계좌 상태입니다.");
        }
        if (request.getRefundAmount().compareTo(virtualAccount.getAmount()) > 0) {
            throw new IllegalArgumentException("환불 금액이 결제 금액을 초과했습니다.");
        }
    }

    private GatewayVirtualAccountIssueResponse toIssueResponse(DummyVirtualAccount virtualAccount, String message) {
        return GatewayVirtualAccountIssueResponse.builder()
                .paymentNo(virtualAccount.getPaymentNo())
                .bankCompany(virtualAccount.getBankCompany())
                .bankName(virtualAccount.getBankName())
                .accountNumber(virtualAccount.getAccountNumber())
                .depositorName(virtualAccount.getDepositorName())
                .amount(virtualAccount.getAmount())
                .expiresAt(virtualAccount.getExpiresAt())
                .issued(true)
                .message(message)
                .build();
    }

    private GatewayVirtualAccountDepositResponse toDepositResponse(DummyVirtualAccount virtualAccount, String message) {
        return GatewayVirtualAccountDepositResponse.builder()
                .paymentNo(virtualAccount.getPaymentNo())
                .bankCompany(virtualAccount.getBankCompany())
                .bankName(virtualAccount.getBankName())
                .accountNumber(virtualAccount.getAccountNumber())
                .depositorName(virtualAccount.getDepositorName())
                .amount(virtualAccount.getAmount())
                .status(virtualAccount.getStatus())
                .expiresAt(virtualAccount.getExpiresAt())
                .depositedAt(virtualAccount.getDepositedAt())
                .message(message)
                .build();
    }
}
