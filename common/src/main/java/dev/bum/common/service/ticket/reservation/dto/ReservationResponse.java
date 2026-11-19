package dev.bum.common.service.ticket.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationResponse {

    // 예매 식별자.
    private long reservationId;

    // 주문 식별자.
    private String orderId;

    // 사용자 ID.
    private String userId;

    // 공연 식별자.
    private long eventId;

    // 공연 제목.
    private String eventTitle;

    // 공연 포스터 URL.
    private String posterUrl;

    // 예매 취소 가능 기한.
    private String cancelDeadlineAt;

    // 예매일.
    private String reservedDate;

    // 공연 시작 일시.
    private String eventDateTime;

    // 공연장 이름.
    private String venue;

    // 티켓 수량.
    private int ticketCount;

    // 예매 상태.
    private String status;
}
