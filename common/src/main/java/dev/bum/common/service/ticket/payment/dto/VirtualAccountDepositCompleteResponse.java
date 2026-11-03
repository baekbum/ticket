package dev.bum.common.service.ticket.payment.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentStatus;

/** 입금 반영과 취소된 예매를 구분하여 PG의 입금 통지 재시도를 종료한다. */
public record VirtualAccountDepositCompleteResponse(
        // 처리 결과.
        Result result,

        // 결제 정보.
        PaymentResponse payment
) {

    public enum Result { // 변경 반영 완료 결과.
        APPLIED, // 예매 취소 완료 결과.

        RESERVATION_CANCELLED
    }

    // 전달받은 값으로 가상계좌 객체를 구성한다.
    public static VirtualAccountDepositCompleteResponse of(PaymentResponse payment) {

        if (payment == null) throw new IllegalArgumentException("입금 처리 결과가 필요합니다.");
        return new VirtualAccountDepositCompleteResponse(
                payment.getStatus() == PaymentStatus.CANCELLED ? Result.RESERVATION_CANCELLED : Result.APPLIED,
                payment
        );
    }
}
