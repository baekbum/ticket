package dev.bum.common.service.ticket.reservation.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CancelReservationResponse(
        // 예매 식별자.
        Long reservationId,

        // 결제 환불 처리 식별자.
        Long paymentRefundProcessId,

        // 이전 요청 복구 여부.
        boolean previousRequestRecovered,

        // 취소한 티켓 식별자 목록.
        List<Long> cancelledTicketIds,

        // 처리 결과 메시지.
        String message
) {

    // 전달받은 값으로 예매 객체를 초기화한다.
    public CancelReservationResponse {

        cancelledTicketIds = List.copyOf(cancelledTicketIds);
    }

    // 게이트웨이 환불 없이 취소가 완료된 결과를 구성한다.
    public static CancelReservationResponse canceledWithoutGateway(Long reservationId, List<Long> ticketIds) {

        return CancelReservationResponse.builder()
                .reservationId(reservationId)
                .paymentRefundProcessId(null)
                .previousRequestRecovered(false)
                .cancelledTicketIds(ticketIds)
                .message("입금 전 예매 취소가 완료되었습니다.")
                .build();
    }

    // 취소 완료 결과를 구성한다.
    public static CancelReservationResponse canceled(Long reservationId, Long processId, List<Long> ticketIds) {

        return CancelReservationResponse.builder()
                .reservationId(reservationId)
                .paymentRefundProcessId(processId)
                .previousRequestRecovered(false)
                .cancelledTicketIds(ticketIds)
                .message("예매 취소가 완료되었습니다.")
                .build();
    }

    // 이전 요청을 복구한 결과를 구성한다.
    public static CancelReservationResponse recovered(Long reservationId, Long processId, List<Long> ticketIds) {

        return CancelReservationResponse.builder()
                .reservationId(reservationId)
                .paymentRefundProcessId(processId)
                .previousRequestRecovered(true)
                .cancelledTicketIds(ticketIds)
                .message("이전 요청의 환불·취소를 완료했습니다. 이번 변경 요청은 추가로 처리하지 않았습니다. 남은 좌석은 다시 취소를 신청해 주세요.")
                .build();
    }
}
