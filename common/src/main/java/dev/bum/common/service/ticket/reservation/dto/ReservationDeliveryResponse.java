package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDeliveryResponse {

    // 예매 배송 정보 식별자.
    private Long reservationDeliveryId;

    // 예매 식별자.
    private Long reservationId;

    // 수령인 이름.
    private String recipientName;

    // 수령인 연락처.
    private String recipientPhone;

    // 우편번호.
    private String zipCode;

    // 주소.
    private String address;

    // 상세 주소.
    private String detailAddress;

    // 배송 요청 사항.
    private String deliveryMessage;

    // 예매 배송 정보 상태.
    private ReservationDeliveryStatus status;

    // 택배사.
    private String carrier;

    // 운송장 번호.
    private String trackingNumber;

    // 배송 시작 시각.
    private String shippedAt;

    // 전달 또는 배송 완료 시각.
    private String deliveredAt;
}
