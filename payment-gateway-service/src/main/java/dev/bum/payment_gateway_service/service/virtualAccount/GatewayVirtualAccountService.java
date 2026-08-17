package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
public class GatewayVirtualAccountService {

    private static final int MAX_ACCOUNT_ISSUE_ATTEMPTS = 5;

    private final DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;
    private final DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;

    public GatewayVirtualAccountIssueResponse issue(GatewayVirtualAccountIssueRequest request) {
        return dummyVirtualAccountJpaRepository.findByPaymentNo(request.getPaymentNo())
                .map(virtualAccount -> toIssueResponse(virtualAccount, "이미 발급된 가상계좌입니다."))
                .orElseGet(() -> issueNewVirtualAccount(request));
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
}
