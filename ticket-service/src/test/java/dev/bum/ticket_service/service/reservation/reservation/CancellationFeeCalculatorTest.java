package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationFeeCalculatorTest {

    private static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 9, 15, 12, 0);

    @ParameterizedTest(name = "관람일 {0}일 전에는 {1}% 수수료를 적용한다")
    @CsvSource({
            "10, 0, 100000",
            "9, 10, 90000",
            "7, 10, 90000",
            "6, 20, 80000",
            "3, 20, 80000",
            "2, 30, 70000",
            "1, 30, 70000"
    })
    @DisplayName("관람일까지 남은 날짜의 경계에 맞춰 취소 수수료를 계산한다")
    void calculate_fee_by_days_before_event(int daysBeforeEvent, int expectedRate, int expectedRefundAmount) {
        Reservation reservation = reservation(CANCELLED_AT.minusDays(8), CANCELLED_AT.plusDays(daysBeforeEvent));

        int refundAmount = CancellationFeeCalculator.calculateRefundAmount(
                reservation, 100000, List.of(ticket(reservation, 100000)), CANCELLED_AT);

        assertThat(CancellationFeeCalculator.resolveFeeRate(reservation, CANCELLED_AT)).isEqualTo(expectedRate);
        assertThat(refundAmount).isEqualTo(expectedRefundAmount);
    }

    @Test
    @DisplayName("예매 후 정확히 7일 이내에는 관람일이 임박해도 수수료가 없다")
    void waive_fee_during_booking_grace_period() {
        Reservation reservation = reservation(CANCELLED_AT.minusDays(7), CANCELLED_AT.plusDays(1));

        int refundAmount = CancellationFeeCalculator.calculateRefundAmount(
                reservation, 100000, List.of(ticket(reservation, 100000)), CANCELLED_AT);

        assertThat(refundAmount).isEqualTo(100000);
    }

    @Test
    @DisplayName("취소 수수료가 실결제 환불 예정액보다 크면 환불액은 0원이다")
    void do_not_return_negative_refund_amount() {
        Reservation reservation = reservation(CANCELLED_AT.minusDays(8), CANCELLED_AT.plusDays(1));

        int refundAmount = CancellationFeeCalculator.calculateRefundAmount(
                reservation, 20000, List.of(ticket(reservation, 100000)), CANCELLED_AT);

        assertThat(refundAmount).isZero();
    }

    @Test
    @DisplayName("관람일 당일에는 예매 후 7일 이내라도 취소할 수 없다")
    void reject_cancel_on_event_date() {
        Reservation reservation = reservation(CANCELLED_AT.minusDays(1), CANCELLED_AT.plusHours(5));

        assertThatThrownBy(() -> CancellationFeeCalculator.calculateRefundAmount(
                reservation, 100000, List.of(ticket(reservation, 100000)), CANCELLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관람일 당일 또는 이후에는 취소할 수 없습니다.");
    }

    @Test
    @DisplayName("18만원 티켓 한 장 취소 시 예매·배송비·쿠폰은 유지하고 20% 취소 수수료만 차감한다")
    void calculate_one_ticket_refund_with_non_refundable_booking_fee() {
        Reservation reservation = reservation(CANCELLED_AT.minusDays(8), CANCELLED_AT.plusDays(3));
        Ticket firstTicket = ticket(1L, reservation, 180000);
        Ticket secondTicket = ticket(2L, reservation, 180000);
        Payment payment = payment(reservation);

        CancellationFeeCalculator.CancellationRefundCalculation calculation = CancellationFeeCalculator.calculate(
                reservation,
                payment,
                List.of(firstTicket, secondTicket),
                List.of(firstTicket),
                List.of(discount(reservation)),
                false,
                CANCELLED_AT);

        assertThat(calculation.refundAmount()).isEqualTo(144000);
        assertThat(calculation.cancellationFeeAmount()).isEqualTo(36000);
        assertThat(calculation.deliveryRefundAmount()).isZero();
        assertThat(calculation.discountDeductionAmount()).isZero();
        assertThat(calculation.restoreCoupon()).isFalse();
    }

    @Test
    @DisplayName("18만원 티켓 두 장 전체 취소 시 배송비와 쿠폰을 정산하고 20% 취소 수수료를 차감한다")
    void calculate_full_refund_with_delivery_coupon_and_cancellation_fee() {
        Reservation reservation = reservation(CANCELLED_AT.minusDays(8), CANCELLED_AT.plusDays(3));
        Ticket firstTicket = ticket(1L, reservation, 180000);
        Ticket secondTicket = ticket(2L, reservation, 180000);
        Payment payment = payment(reservation);

        CancellationFeeCalculator.CancellationRefundCalculation calculation = CancellationFeeCalculator.calculate(
                reservation,
                payment,
                List.of(firstTicket, secondTicket),
                List.of(firstTicket, secondTicket),
                List.of(discount(reservation)),
                true,
                CANCELLED_AT);

        assertThat(calculation.refundAmount()).isEqualTo(281200);
        assertThat(calculation.cancellationFeeAmount()).isEqualTo(72000);
        assertThat(calculation.deliveryRefundAmount()).isEqualTo(3200);
        assertThat(calculation.discountDeductionAmount()).isEqualTo(10000);
        assertThat(calculation.restoreCoupon()).isTrue();
    }

    @Test
    @DisplayName("두 티켓을 순차 취소해도 예매 수수료는 제외되고 배송비와 쿠폰은 마지막 취소에서 정산된다")
    void calculate_sequential_ticket_refunds() {
        Reservation reservation = reservation(CANCELLED_AT.minusDays(8), CANCELLED_AT.plusDays(3));
        Ticket firstTicket = ticket(1L, reservation, 180000);
        Ticket secondTicket = ticket(2L, reservation, 180000);
        Payment payment = payment(reservation);
        ReservationDiscount discount = discount(reservation);

        CancellationFeeCalculator.CancellationRefundCalculation firstCalculation = CancellationFeeCalculator.calculate(
                reservation,
                payment,
                List.of(firstTicket, secondTicket),
                List.of(firstTicket),
                List.of(discount),
                false,
                CANCELLED_AT);
        payment.applyCancellation(firstCalculation.refundAmount(), firstCalculation.cancellationFeeAmount());

        CancellationFeeCalculator.CancellationRefundCalculation lastCalculation = CancellationFeeCalculator.calculate(
                reservation,
                payment,
                List.of(secondTicket),
                List.of(secondTicket),
                List.of(discount),
                true,
                CANCELLED_AT);

        assertThat(firstCalculation.refundAmount()).isEqualTo(144000);
        assertThat(lastCalculation.refundAmount()).isEqualTo(137200);
        assertThat(firstCalculation.refundAmount() + lastCalculation.refundAmount()).isEqualTo(281200);
        assertThat(lastCalculation.deliveryRefundAmount()).isEqualTo(3200);
        assertThat(lastCalculation.discountDeductionAmount()).isEqualTo(10000);
    }

    private Reservation reservation(LocalDateTime reservedAt, LocalDateTime eventDateTime) {
        Event event = Event.builder()
                .eventId(1L)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(eventDateTime)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
        return Reservation.builder()
                .reservationId(1L)
                .orderId("ORDER-1")
                .userId("user01")
                .event(event)
                .status(ReservationStatus.PAID)
                .reservedAt(reservedAt)
                .build();
    }

    private Payment payment(Reservation reservation) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PAID)
                .amount(361200)
                .reservationFeeAmount(8000)
                .deliveryFeeAmount(3200)
                .requestedAt(CANCELLED_AT.minusDays(8))
                .build();
    }

    private ReservationDiscount discount(Reservation reservation) {
        Coupon coupon = Coupon.builder()
                .couponId(1L)
                .name("1만원 할인")
                .code("FIXED-10000")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(10000)
                .minOrderAmount(100000)
                .status(CouponStatus.ACTIVE)
                .build();
        UserCoupon userCoupon = UserCoupon.builder()
                .userCouponId(1L)
                .userId("user01")
                .coupon(coupon)
                .status(UserCouponStatus.USED)
                .issuedAt(CANCELLED_AT.minusDays(30))
                .usedAt(CANCELLED_AT.minusDays(8))
                .build();
        return ReservationDiscount.builder()
                .reservation(reservation)
                .userCoupon(userCoupon)
                .discountType(DiscountType.COUPON)
                .discountName("1만원 할인")
                .couponDiscountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(10000)
                .discountAmount(10000)
                .build();
    }

    private Ticket ticket(Reservation reservation, int price) {
        return ticket(1L, reservation, price);
    }

    private Ticket ticket(Long ticketId, Reservation reservation, int price) {
        Seat seat = Seat.builder()
                .seatId(ticketId)
                .event(reservation.getEvent())
                .zone("VIP")
                .seatRow(1)
                .seatCol(ticketId.intValue())
                .grade(SeatGrade.VIP)
                .price(price)
                .status(SeatStatus.RESERVED)
                .build();
        return Ticket.builder()
                .ticketId(ticketId)
                .userId(reservation.getUserId())
                .reservation(reservation)
                .event(reservation.getEvent())
                .seat(seat)
                .status(TicketStatus.PAID)
                .build();
    }
}
