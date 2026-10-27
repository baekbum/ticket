package dev.bum.common.service.ticket.reservation.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CancelReservationResponse(
        Long reservationId,
        Long paymentRefundProcessId,
        boolean previousRequestRecovered,
        List<Long> cancelledTicketIds,
        String message
) {
    public CancelReservationResponse {
        cancelledTicketIds = List.copyOf(cancelledTicketIds);
    }

    public static CancelReservationResponse canceledWithoutGateway(Long reservationId, List<Long> ticketIds) {
        return CancelReservationResponse.builder()
                .reservationId(reservationId)
                .paymentRefundProcessId(null)
                .previousRequestRecovered(false)
                .cancelledTicketIds(ticketIds)
                .message("입금 전 예매 취소가 완료되었습니다.")
                .build();
    }

    public static CancelReservationResponse canceled(Long reservationId, Long processId, List<Long> ticketIds) {
        return CancelReservationResponse.builder()
                .reservationId(reservationId)
                .paymentRefundProcessId(processId)
                .previousRequestRecovered(false)
                .cancelledTicketIds(ticketIds)
                .message("예매 취소가 완료되었습니다.")
                .build();
    }

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
