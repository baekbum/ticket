package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.reservation.reservation.ReservationManagementService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ReservationManagementServiceTest {

    @InjectMocks
    private ReservationManagementService reservationManagementService;

    @Mock
    private ReservationRepository repository;

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private CardPaymentRefundService cardPaymentRefundService;

    @Mock
    private VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;

    @Mock
    private TicketJpaRepository ticketJpaRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    @Mock
    private ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    @Test
    @DisplayName("ID로 예약을 조회한다")
    void reservation_select_by_id() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        new Ticket(1L, "user01", reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);

        given(repository.selectById(1L)).willReturn(reservation);

        ReservationResponse response = reservationManagementService.selectById(1L);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getUserId()).isEqualTo("user01");
        assertThat(response.getTicketCount()).isEqualTo(1);
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("조건으로 예약 목록을 조회한다")
    void reservation_select_by_cond() {
        ReservationCondRequest cond = ReservationCondRequest.builder()
                .userId("user01")
                .eventId(1L)
                .page(0)
                .size(10)
                .sort(List.of("reservationId-desc"))
                .build();
        Reservation reservation = reservation(1L, "order-1", "user01", event());

        given(repository.selectByCond(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(reservation)));

        CustomPageResponse<ReservationResponse> response = reservationManagementService.selectByCond(cond);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getReservationId()).isEqualTo(1L);
        assertThat(response.getPage().getTotalElements()).isEqualTo(1);
        then(repository).should().selectByCond(
                eq(cond),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort().getOrderFor("reservationId") != null)
        );
    }

    @Test
    @DisplayName("예약 취소 후 좌석 캐시와 구매 제한 캐시를 갱신한다")
    void reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket selectedTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PENDING_PAYMENT);

        given(repository.selectById(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(selectedTicket.getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        then(seatCacheService).should().syncAvailableSeatsAfterCommit(List.of(selectedTicket.getSeat()));
        then(seatCacheService).should().updateUserPurchaseLimit(selectedTicket.getSeat().getEvent(), "user01", 1, "SUB");
    }

    @Test
    @DisplayName("카드 결제 완료 예매 부분 취소는 선택 티켓 비율만큼 부분 환불한다")
    void refund_card_payment_before_partial_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket selectedTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(cardPaymentRefundService).should().refundPartial(payment, 125000);
    }

    @Test
    @DisplayName("카드 결제 완료 예매 전체 취소는 gateway 환불 후 예매를 취소한다")
    void refund_card_payment_before_full_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 2L))
                .build();
        Ticket firstTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));
        given(reservationDiscountJpaRepository.findByReservation(reservation))
                .willReturn(List.of(reservationDiscount(reservation, userCoupon)));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getUsedAt()).isNull();
        then(cardPaymentRefundService).should().refundAll(payment);
    }

    @Test
    @DisplayName("무통장 결제 완료 예매 전체 취소는 gateway 환불 후 예매를 취소한다")
    void refund_virtual_account_payment_before_full_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 2L))
                .refundBankCompany(BankCompany.KB)
                .refundAccountNumber("123-456-7890")
                .refundAccountHolder("홍길동")
                .build();
        Ticket firstTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(virtualAccountPaymentRefundService).should().refundAll(payment, BankCompany.KB, "123-456-7890", "홍길동");
        then(cardPaymentRefundService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("부분 환불 이력이 있는 예매의 마지막 티켓 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_last_ticket_cancel_after_partial_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PARTIALLY_CANCELLED);
        Payment payment = cardPayment(reservation, PaymentStatus.PARTIALLY_REFUNDED, 125000);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(2L))
                .build();
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(remainingTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(remainingTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(cardPaymentRefundService).should().refundAll(payment);
        then(reservationDiscountJpaRepository).shouldHaveNoInteractions();
    }

    private Reservation reservation(Long reservationId, String orderId, String userId, Event event) {
        return reservation(reservationId, orderId, userId, event, ReservationStatus.PENDING_PAYMENT);
    }

    private Reservation reservation(Long reservationId, String orderId, String userId, Event event, ReservationStatus status) {
        return Reservation.builder()
                .reservationId(reservationId)
                .orderId(orderId)
                .userId(userId)
                .event(event)
                .status(status)
                .reservedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Payment cardPayment(Reservation reservation) {
        return cardPayment(reservation, PaymentStatus.PAID, 0);
    }

    private Payment cardPayment(Reservation reservation, PaymentStatus status, Integer refundedAmount) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(status)
                .amount(250000)
                .refundedAmount(refundedAmount)
                .cardInfo(CardPaymentInfo.builder()
                        .transactionId("CARD-1")
                        .cardCompany(CardCompany.SHINHAN)
                        .maskedCardNumber("4111-****-****-1111")
                        .build())
                .requestedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Payment virtualAccountPayment(Reservation reservation) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PAID)
                .amount(250000)
                .requestedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private UserCoupon usedUserCoupon() {
        return UserCoupon.builder()
                .userId("user01")
                .status(UserCouponStatus.USED)
                .issuedAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .usedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .expiresAt(LocalDateTime.of(2026, 12, 31, 23, 59))
                .build();
    }

    private ReservationDiscount reservationDiscount(Reservation reservation, UserCoupon userCoupon) {
        return ReservationDiscount.builder()
                .reservation(reservation)
                .userCoupon(userCoupon)
                .build();
    }

    private Ticket ticket(Long ticketId, Reservation reservation, Event event, Seat seat, TicketStatus status) {
        return Ticket.builder()
                .ticketId(ticketId)
                .userId(reservation.getUserId())
                .reservation(reservation)
                .event(event)
                .seat(seat)
                .status(status)
                .build();
    }

    private Event event() {
        return Event.builder()
                .eventId(1L)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private Seat seat(Long seatId, Event event, String zone, Integer row, Integer col) {
        return Seat.builder()
                .seatId(seatId)
                .event(event)
                .zone(zone)
                .seatRow(row)
                .seatCol(col)
                .grade(SeatGrade.VIP)
                .price(150000)
                .status(SeatStatus.AVAILABLE)
                .build();
    }
}
