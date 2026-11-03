package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentRefundHistoryResponse;
import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDetailResponse {

    // 예매 정보.
    private ReservationResponse reservation;

    // 티켓 목록.
    @Builder.Default
    private List<TicketResponse> tickets = new ArrayList<>();

    // 적용한 할인 목록.
    @Builder.Default
    private List<ReservationDiscountResponse> discounts = new ArrayList<>();

    // 배송 정보.
    private ReservationDeliveryResponse delivery;

    // 결제 정보.
    private PaymentResponse payment;

    // 환불 이력 목록.
    @Builder.Default
    private List<PaymentRefundHistoryResponse> refundHistories = new ArrayList<>();

    // 전체 티켓 금액.
    private Integer totalTicketAmount;

    // 전체 할인 금액.
    private Integer totalDiscountAmount;

    // 실제 결제 금액.
    private Integer paymentAmount;
}
