package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundResponse;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefund;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefundJpaRepository;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEvent;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEventJpaRepository;
import dev.bum.payment_gateway_service.service.refund.GatewayRefundProcessService;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GatewayVirtualAccountService {

    private static final int MAX_ACCOUNT_ISSUE_ATTEMPTS = 5;

    private final DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;
    private final DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;
    private final GatewayRefundJpaRepository refunds;
    private final GatewayRefundProcessService refundProcesses;
    private final VirtualAccountDepositOutboxService depositOutboxService;
    private final VirtualAccountOutboxEventJpaRepository expirationOutbox;

    @Transactional(readOnly = true)
    public GatewayVirtualAccountStatusResponse status(String paymentNo) {
        DummyVirtualAccount account = dummyVirtualAccountJpaRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new IllegalArgumentException("가상계좌 정보를 찾을 수 없습니다."));
        return new GatewayVirtualAccountStatusResponse(
                account.getPaymentNo(), account.getBankCompany(), account.getBankName(),
                account.getAccountNumber(), account.getDepositorName(), account.getAmount(),
                account.getExpiresAt(), account.getDepositedAt(),
                GatewayVirtualAccountStatus.valueOf(account.getStatus().name())
        );
    }

    public GatewayVirtualAccountIssueResponse issue(GatewayVirtualAccountIssueRequest request) {
        return dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .map(virtualAccount -> {
                    validateSameIssueRequest(virtualAccount, request);
                    expireIfNecessary(virtualAccount);

                    return toIssueResponse(virtualAccount, "이미 발급된 가상계좌입니다.");
                })
                .orElseGet(() -> issueNewVirtualAccount(request));
    }

    public GatewayVirtualAccountDepositResponse deposit(GatewayVirtualAccountDepositRequest request) {
        DummyVirtualAccount virtualAccount = dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("가상계좌 정보를 찾을 수 없습니다."));

        if (virtualAccount.hasDeposit()) {
            validateSameDeposit(virtualAccount, request);
            return toDepositResponse(virtualAccount, "이미 입금 처리된 가상계좌입니다.");
        }

        LocalDateTime depositedAt = (request.getDepositedAt() == null ? LocalDateTime.now() : request.getDepositedAt()).truncatedTo(ChronoUnit.MICROS);
        validateDeposit(virtualAccount, request, depositedAt);
        virtualAccount.deposit(request.getDepositorName(), depositedAt);

        dummyVirtualAccountPaymentHistoryJpaRepository.save(DummyVirtualAccountPaymentHistory.deposited(virtualAccount));
        // 입금·이력·전달할 알림을 함께 커밋한다. 티켓 호출은 커밋 이후에만 실행한다.
        depositOutboxService.enqueue(virtualAccount);
        return toDepositResponse(virtualAccount, "가상계좌 입금이 완료되었습니다. 티켓 서비스에 입금 정보를 전달합니다.");
    }

    public GatewayVirtualAccountRefundResponse refund(GatewayVirtualAccountRefundRequest request) {
        if (request.getPaymentRefundProcessId() == null || request.getPaymentRefundProcessId() <= 0) {
            throw new IllegalArgumentException("환불 프로세스 ID가 필요합니다.");
        }
        // 원래 가상계좌 행을 잠가 같은 결제에 대한 환불과 결과 확인을 직렬화한다.
        DummyVirtualAccount virtualAccount = dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("가상계좌 정보를 찾을 수 없습니다."));
        GatewayRefund refund = refundProcesses.prepare(request);
        if (refund.isSucceeded()) return refund.replay(request);

        try {
            validateRefund(virtualAccount, request);
            dummyVirtualAccountPaymentHistoryJpaRepository.save(
                    DummyVirtualAccountPaymentHistory.refunded(
                            virtualAccount,
                            request.getRefundBankCompany(),
                            request.getRefundAccountNumber(),
                            request.getRefundAccountHolder(),
                            request.getRefundAmount()
                    )
            );
            log.info(
                    "무통장 환불 성공: paymentNo={}, refundBank={}, refundAccount={}, refundAccountHolder={}, refundAmount={}",
                    request.getPaymentNo(),
                    request.getRefundBankCompany(),
                    request.getRefundAccountNumber(),
                    request.getRefundAccountHolder(),
                    request.getRefundAmount()
            );

            GatewayVirtualAccountRefundResponse response = GatewayVirtualAccountRefundResponse.builder()
                    .paymentNo(request.getPaymentNo())
                    .refundBankCompany(request.getRefundBankCompany())
                    .refundAccountNumber(request.getRefundAccountNumber())
                    .refundAccountHolder(request.getRefundAccountHolder())
                    .refundedAmount(request.getRefundAmount().setScale(2, RoundingMode.UNNECESSARY))
                    .message("무통장 환불 입금이 완료되었습니다.")
                    .build();
            // 실제 입금 환불 이력과 성공 상태를 같은 A 트랜잭션에서 확정한다.
            refund.succeeded(response);
            return response;
        } catch (RuntimeException e) {
            refund.failed(e.getMessage());
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

        return toIssueResponse(virtualAccount, "가상계좌가 발급되었습니다.");
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

    private void validateDeposit(DummyVirtualAccount virtualAccount, GatewayVirtualAccountDepositRequest request,
                                 LocalDateTime depositedAt) {
        if (virtualAccount.getStatus() != VirtualAccountPaymentStatus.WAITING_DEPOSIT) {
            throw new IllegalArgumentException("입금 처리할 수 없는 가상계좌 상태입니다.");
        }
        if (depositedAt.isAfter(virtualAccount.getExpiresAt())) {
            virtualAccount.expire();
            throw new IllegalArgumentException("입금 기한이 만료되었습니다.");
        }
        if (virtualAccount.getAmount().compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("입금 금액이 일치하지 않습니다.");
        }
    }

    private void validateSameDeposit(DummyVirtualAccount account, GatewayVirtualAccountDepositRequest request) {
        if (account.getAmount().compareTo(request.getAmount()) != 0
                || !Objects.equals(account.getDepositorName(), request.getDepositorName())
                || (request.getDepositedAt() != null
                    && !Objects.equals(account.getDepositedAt(), request.getDepositedAt().truncatedTo(ChronoUnit.MICROS)))) {
            throw new IllegalArgumentException("이미 처리된 입금 정보와 요청이 일치하지 않습니다.");
        }
    }

    private void validateSameIssueRequest(
            DummyVirtualAccount virtualAccount,
            GatewayVirtualAccountIssueRequest request
    ) {
        boolean sameBankCompany = virtualAccount.getBankCompany() == request.getBankCompany();
        boolean sameAmount = virtualAccount.getAmount().compareTo(request.getAmount()) == 0;

        if (!sameBankCompany || !sameAmount) {
            throw new IllegalArgumentException("동일한 결제번호로 다른 가상계좌 발급 정보를 요청할 수 없습니다.");
        }
    }

    private void expireIfNecessary(DummyVirtualAccount virtualAccount) {
        LocalDateTime expiredAt = LocalDateTime.now();
        if (virtualAccount.getStatus() == VirtualAccountPaymentStatus.WAITING_DEPOSIT
                && expiredAt.isAfter(virtualAccount.getExpiresAt())) {
            virtualAccount.expire();
            // 발급 재조회에서 만료를 확정해도 티켓에 전달할 이력과 알림을 함께 남긴다.
            dummyVirtualAccountPaymentHistoryJpaRepository.save(DummyVirtualAccountPaymentHistory.expired(virtualAccount));
            expirationOutbox.save(VirtualAccountOutboxEvent.virtualAccountExpired(virtualAccount, expiredAt));
        }
    }

    private void validateRefund(DummyVirtualAccount virtualAccount, GatewayVirtualAccountRefundRequest request) {
        if (!virtualAccount.hasDeposit()) {
            throw new IllegalArgumentException("환불할 수 없는 가상계좌 상태입니다.");
        }
        BigDecimal refunded = refunds.sumRefundAmount(virtualAccount.getPaymentNo(), PaymentMethod.BANK_TRANSFER);
        if (refunded == null) refunded = BigDecimal.ZERO;
        if (request.getRefundAmount() == null || request.getRefundAmount().signum() <= 0) {
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
        }
        if (request.getRefundAmount().compareTo(virtualAccount.getAmount().subtract(refunded)) > 0) {
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
                .status(GatewayVirtualAccountStatus.valueOf(virtualAccount.getStatus().name()))
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
