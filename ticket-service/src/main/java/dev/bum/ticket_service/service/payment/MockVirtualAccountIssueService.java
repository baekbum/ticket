package dev.bum.ticket_service.service.payment;

import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MockVirtualAccountIssueService {

    private static final LocalTime DEPOSIT_DEADLINE_TIME = LocalTime.of(23, 59, 59);

    /**
     * 실제 은행 가상계좌 발급 연동을 대신하는 토이 프로젝트용 계좌 발급 시뮬레이터.
     * 은행 코드에 맞는 prefix와 랜덤 숫자를 조합해 입금 계좌번호를 만든다.
     */
    private static final List<MockBank> MOCK_BANKS = List.of(
            new MockBank("KB", "KB국민은행", "1111"),
            new MockBank("SHINHAN", "신한은행", "2222"),
            new MockBank("WOORI", "우리은행", "3333"),
            new MockBank("HANA", "하나은행", "4444")
    );

    public VirtualAccount issue(String bankCode) {
        MockBank bank = findBank(bankCode);
        return new VirtualAccount(
                bank.getBankName(),
                generateAccountNumber(bank.getAccountPrefix()),
                LocalDateTime.now().with(DEPOSIT_DEADLINE_TIME)
        );
    }

    private MockBank findBank(String bankCode) {
        String normalizedBankCode = normalizeBankCode(bankCode);
        return MOCK_BANKS.stream()
                .filter(bank -> bank.getBankCode().equals(normalizedBankCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 은행 코드입니다."));
    }

    private String normalizeBankCode(String bankCode) {
        return StringUtils.hasText(bankCode) ? bankCode.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String generateAccountNumber(String accountPrefix) {
        return String.format(
                "%s-%04d-%04d-%04d",
                accountPrefix,
                randomFourDigits(),
                randomFourDigits(),
                randomFourDigits()
        );
    }

    private int randomFourDigits() {
        return ThreadLocalRandom.current().nextInt(0, 10_000);
    }

    @Getter
    public static class VirtualAccount {

        private final String bankName;
        private final String accountNumber;
        private final LocalDateTime expiresAt;

        public VirtualAccount(String bankName, String accountNumber, LocalDateTime expiresAt) {
            this.bankName = bankName;
            this.accountNumber = accountNumber;
            this.expiresAt = expiresAt;
        }
    }

    @Getter
    private static class MockBank {

        private final String bankCode;
        private final String bankName;
        private final String accountPrefix;

        private MockBank(String bankCode, String bankName, String accountPrefix) {
            this.bankCode = bankCode;
            this.bankName = bankName;
            this.accountPrefix = accountPrefix;
        }
    }
}
