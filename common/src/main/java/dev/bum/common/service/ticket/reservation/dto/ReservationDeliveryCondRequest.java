package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDeliveryCondRequest {

    // 예매 식별자.
    private Long reservationId;

    // 사용자 ID.
    private String userId;

    // 예매 배송 정보 상태.
    private ReservationDeliveryStatus status;

    // 조회할 페이지.
    @Builder.Default
    private Integer page = 0;

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10;

    // 조회 결과 정렬 조건.
    private List<String> sort;
}
