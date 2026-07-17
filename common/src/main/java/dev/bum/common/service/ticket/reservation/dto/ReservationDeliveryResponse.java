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

    private Long reservationDeliveryId;
    private Long reservationId;
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private String detailAddress;
    private String deliveryMessage;
    private ReservationDeliveryStatus status;
    private String carrier;
    private String trackingNumber;
    private String shippedAt;
    private String deliveredAt;
}
