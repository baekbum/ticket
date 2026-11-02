package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentStatus;

/** 입금 반영과 취소된 예매를 구분하여 PG의 입금 통지 재시도를 종료한다. */
public record VirtualAccountDepositCompleteResponse(Result result, PaymentResponse payment) {
    public enum Result { APPLIED, RESERVATION_CANCELLED }

    public static VirtualAccountDepositCompleteResponse of(PaymentResponse payment) {
        if (payment == null) throw new IllegalArgumentException("입금 처리 결과가 필요합니다.");
        return new VirtualAccountDepositCompleteResponse(
                payment.getStatus() == PaymentStatus.CANCELLED ? Result.RESERVATION_CANCELLED : Result.APPLIED,
                payment
        );
    }
}
