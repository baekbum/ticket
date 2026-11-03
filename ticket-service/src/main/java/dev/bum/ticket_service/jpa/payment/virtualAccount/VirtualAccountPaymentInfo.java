package dev.bum.ticket_service.jpa.payment.virtualAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountPaymentInfo {

    // 은행 이름.
    @Column(name = "bank_name", length = 50)
    private String bankName;

    // 계좌 번호.
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    // 입금자 이름.
    @Column(name = "depositor_name", length = 50)
    private String depositorName;

    // 가상계좌를 발급한다.
    public void issue(String bankName, String accountNumber) {

        this.bankName = bankName;
        this.accountNumber = accountNumber;
    }

    // 가상계좌 처리에서 입금 결과를 반영한다.
    public void deposit(String depositorName) {

        this.depositorName = depositorName;
    }
}
