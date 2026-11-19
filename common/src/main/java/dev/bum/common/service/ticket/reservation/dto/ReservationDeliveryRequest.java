package dev.bum.common.service.ticket.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDeliveryRequest {

    // 수령인 이름.
    @NotBlank
    private String recipientName;

    // 수령인 연락처.
    @NotBlank
    private String recipientPhone;

    // 우편번호.
    @NotBlank
    private String zipCode;

    // 주소.
    @NotBlank
    private String address;

    // 상세 주소.
    private String detailAddress;

    // 배송 요청 사항.
    private String deliveryMessage;
}
