package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
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
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessGatewayAttempt;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.reservation.reservation.ReservationService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private ReservationRepository repository;

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentRefundProcessService paymentRefundProcessService;

    @Mock
    private CardPaymentRefundService cardPaymentRefundService;

    @Mock
    private VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;

    @Mock
    private TicketJpaRepository ticketJpaRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    @BeforeEach
    void setUp() {
        lenient().when(paymentRefundProcessService.startGatewayAttempt(any(Payment.class), any(), anyInt(), anyBoolean(), any()))
                .thenReturn(new PaymentRefundProcessGatewayAttempt(1L, true, false));
    }

    @Test
    @DisplayName("본인 예약을 ID로 조회한다")
    void reservation_select_my_reservation() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        given(repository.selectById(1L)).willReturn(reservation);

        ReservationResponse response = reservationService.selectMyReservation("user01", 1L);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo("user01");
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("다른 사용자의 예약은 조회할 수 없다")
    void reservation_select_my_reservation_forbidden() {
        Reservation reservation = reservation(1L, "order-1", "other-user", event());
        given(repository.selectById(1L)).willReturn(reservation);

        assertThatThrownBy(() -> reservationService.selectMyReservation("user01", 1L))
                .isInstanceOf(AccessDeniedException.class);

        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("본인 예약 목록 조회는 로그인 사용자 ID로 검색한다")
    void reservation_select_my_reservations() {
        ReservationCondRequest cond = ReservationCondRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .page(0)
                .size(10)
                .build();
        Reservation reservation = reservation(1L, "order-1", "user01", event());

        given(repository.selectByCond(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(reservation)));

        CustomPageResponse<ReservationResponse> response = reservationService.selectMyReservations("user01", cond);

        assertThat(cond.getUserId()).isEqualTo("user01");
        assertThat(response.getContent()).hasSize(1);
        then(repository).should().selectByCond(eq(cond), any(Pageable.class));
    }

    @Test
    @DisplayName("본인 예약 취소는 로그인 사용자 ID로 취소한다")
    void reservation_cancel_my_reservation() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket selectedTicket = ticket(1L, reservation, TicketStatus.PENDING_PAYMENT);
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PENDING_PAYMENT);

        given(repository.selectById(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(info.getUserId()).isEqualTo("user01");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(selectedTicket.getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        then(repository).should().selectById(1L);
        then(seatCacheService).should().syncAvailableSeatsAfterCommit(List.of(selectedTicket.getSeat()));
        then(seatCacheService).should().updateUserPurchaseLimit(selectedTicket.getSeat().getEvent(), "user01", 1, "SUB");
    }

    @Test
    @DisplayName("카드 결제 완료 본인 예매 부분 취소는 선택 티켓 비율만큼 부분 환불한다")
    void refund_card_payment_before_partial_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket selectedTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));
        willAnswer(invocation -> {
            payment.partialRefund(125000);
            return 125000;
        }).given(cardPaymentRefundService).refundPartial(payment, 125000);

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(info.getUserId()).isEqualTo("user01");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(cardPaymentRefundService).should().refundPartial(payment, 125000);
        then(paymentRefundProcessService).should().savePaymentRefundHistory(1L, payment, List.of(selectedTicket), 125000, false);
    }

    @Test
    @DisplayName("카드 결제 완료 본인 예매 부분 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_card_payment_partial_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket selectedTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        then(reservationDiscountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("카드 결제 완료 본인 예매 전체 취소는 gateway 환불 후 예매를 취소한다")
    void refund_card_payment_before_full_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 2L))
                .build();
        Ticket firstTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));
        given(reservationDiscountJpaRepository.findByReservation(reservation))
                .willReturn(List.of(reservationDiscount(reservation, userCoupon)));
        willReturn(250000).given(cardPaymentRefundService).refundAll(payment);

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(info.getUserId()).isEqualTo("user01");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getUsedAt()).isNull();
        then(cardPaymentRefundService).should().refundAll(payment);
        then(paymentRefundProcessService).should().savePaymentRefundHistory(1L, payment, List.of(firstTicket, secondTicket), 250000, true);
    }

    @Test
    @DisplayName("gateway 환불 성공 후 본인 예매 취소 재요청은 gateway 재호출 없이 로컬 상태를 반영한다")
    void complete_local_state_without_gateway_call_when_gateway_already_succeeded() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 2L))
                .build();
        Ticket firstTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));
        given(paymentRefundProcessService.startGatewayAttempt(payment, List.of(firstTicket, secondTicket), 250000, true, null))
                .willReturn(new PaymentRefundProcessGatewayAttempt(1L, false, true));

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(250000);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        then(cardPaymentRefundService).should(never()).refundAll(payment);
        then(paymentRefundProcessService).should().savePaymentRefundHistory(1L, payment, List.of(firstTicket, secondTicket), 250000, true);
    }

    @Test
    @DisplayName("무통장 결제 완료 본인 예매 전체 취소는 gateway 환불 후 예매를 취소한다")
    void refund_virtual_account_payment_before_full_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 2L))
                .refundAccount(refundAccount())
                .build();
        Ticket firstTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));
        given(reservationDiscountJpaRepository.findByReservation(reservation))
                .willReturn(List.of(reservationDiscount(reservation, userCoupon)));
        willReturn(250000).given(virtualAccountPaymentRefundService).refundAll(payment, refundAccount());

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getUsedAt()).isNull();
        then(virtualAccountPaymentRefundService).should().refundAll(payment, refundAccount());
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        then(paymentRefundProcessService).should().savePaymentRefundHistory(1L, payment, List.of(firstTicket, secondTicket), 250000, true);
    }

    @Test
    @DisplayName("무통장 결제 완료 본인 예매 부분 취소는 선택 티켓 비율만큼 부분 환불한다")
    void refund_virtual_account_payment_before_partial_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .refundAccount(refundAccount())
                .build();
        Ticket selectedTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));
        willReturn(125000).given(virtualAccountPaymentRefundService).refundPartial(payment, 125000, refundAccount());

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(virtualAccountPaymentRefundService).should().refundPartial(payment, 125000, refundAccount());
        then(paymentRefundProcessService).should().savePaymentRefundHistory(1L, payment, List.of(selectedTicket), 125000, false);
    }

    @Test
    @DisplayName("무통장 결제 완료 본인 예매 부분 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_virtual_account_payment_partial_my_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .refundAccount(refundAccount())
                .build();
        Ticket selectedTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        then(reservationDiscountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("부분 환불 이력이 있는 본인 예매의 마지막 티켓 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_last_ticket_cancel_after_partial_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PARTIALLY_CANCELLED);
        Payment payment = cardPayment(reservation, PaymentStatus.PARTIALLY_REFUNDED, 125000);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(2L))
                .build();
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(remainingTicket));

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(remainingTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(cardPaymentRefundService).should().refundAll(payment);
        then(reservationDiscountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("무통장 부분 환불 이력이 있는 본인 예매의 마지막 티켓 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_last_ticket_cancel_after_virtual_account_partial_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PARTIALLY_CANCELLED);
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.PARTIALLY_REFUNDED, 125000);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(2L))
                .refundAccount(refundAccount())
                .build();
        Ticket remainingTicket = ticket(2L, reservation, TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(java.util.Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(remainingTicket));

        reservationService.cancelMyReservation("user01", 1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(remainingTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(virtualAccountPaymentRefundService).should().refundAll(payment, refundAccount());
        then(reservationDiscountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 취소된 본인 예매는 다시 취소할 수 없고 환불을 요청하지 않는다")
    void reject_cancelled_my_reservation_cancel_before_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.CANCELLED);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();

        given(repository.selectById(1L)).willReturn(reservation);

        assertThatThrownBy(() -> reservationService.cancelMyReservation("user01", 1L, info))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 취소되었거나 만료된 예매입니다.");

        then(ticketJpaRepository).shouldHaveNoInteractions();
        then(paymentJpaRepository).shouldHaveNoInteractions();
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        then(virtualAccountPaymentRefundService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 취소된 티켓을 선택하면 gateway 환불 전에 차단한다")
    void reject_cancelled_ticket_selection_before_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket cancelledTicket = ticket(1L, reservation, TicketStatus.CANCELLED);
        Ticket activeTicket = ticket(2L, reservation, TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(cancelledTicket, activeTicket));

        assertThatThrownBy(() -> reservationService.cancelMyReservation("user01", 1L, info))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("선택한 티켓 중 취소 가능한 예매 티켓이 아닌 항목이 있습니다.");

        then(paymentJpaRepository).shouldHaveNoInteractions();
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        then(virtualAccountPaymentRefundService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("같은 티켓을 중복 선택하면 gateway 환불 전에 차단한다")
    void reject_duplicate_ticket_selection_before_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("other-user")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 1L))
                .build();
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);

        given(repository.selectById(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));

        assertThatThrownBy(() -> reservationService.cancelMyReservation("user01", 1L, info))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("취소할 티켓이 중복 선택되었습니다.");

        then(paymentJpaRepository).shouldHaveNoInteractions();
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        then(virtualAccountPaymentRefundService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 사용자의 예약은 취소할 수 없다")
    void reservation_cancel_my_reservation_forbidden() {
        Reservation reservation = reservation(1L, "order-1", "other-user", event());
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();

        given(repository.selectById(1L)).willReturn(reservation);

        assertThatThrownBy(() -> reservationService.cancelMyReservation("user01", 1L, info))
                .isInstanceOf(AccessDeniedException.class);

        then(repository).should().selectById(1L);
        then(repository).shouldHaveNoMoreInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
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
        return virtualAccountPayment(reservation, PaymentStatus.PAID, 0);
    }

    private Payment virtualAccountPayment(Reservation reservation, PaymentStatus status, Integer refundedAmount) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(status)
                .amount(250000)
                .refundedAmount(refundedAmount)
                .requestedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private RefundAccountRequest refundAccount() {
        return RefundAccountRequest.builder()
                .bankCompany(BankCompany.KB)
                .accountNumber("123-456-7890")
                .accountHolder("홍길동")
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

    private Ticket ticket(Long ticketId, Reservation reservation, TicketStatus status) {
        return Ticket.builder()
                .ticketId(ticketId)
                .userId(reservation.getUserId())
                .reservation(reservation)
                .event(reservation.getEvent())
                .seat(seat(ticketId, reservation.getEvent(), "VIP", 1, ticketId.intValue()))
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
