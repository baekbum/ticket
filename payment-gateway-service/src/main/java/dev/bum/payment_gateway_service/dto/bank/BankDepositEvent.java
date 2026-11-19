package dev.bum.payment_gateway_service.dto.bank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BankDepositEvent(
        // 최초 입금 작업의 안정적인 멱등 식별자.
        @NotBlank String eventId,

        // 은행에 커밋된 최초 입금의 불변 스냅샷.
        @NotNull @Valid BankVirtualAccountDepositResponse deposit
) {

    // 계좌의 현재 상태와 무관하게 최초 입금 이벤트를 고정한다.
    public static BankDepositEvent from(BankVirtualAccountDepositResponse bank) {

        return new BankDepositEvent(bank.pgPaymentId() + ":DEPOSIT:INITIAL", bank);
    }

    // 임의 이벤트 ID로 같은 입금을 새 작업처럼 처리하지 못하게 한다.
    public void validate() {

        if (deposit == null || !(deposit.pgPaymentId() + ":DEPOSIT:INITIAL").equals(eventId)) {
            throw new IllegalArgumentException("은행 최초 입금 이벤트 식별자가 일치하지 않습니다.");
        }
    }
}
