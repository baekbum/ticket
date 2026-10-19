package dev.bum.ticket_service.service;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
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
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.card.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.refund.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessGatewayAttempt;
import dev.bum.ticket_service.service.payment.refund.RefundAction;
import dev.bum.ticket_service.service.reservation.reservation.ReservationCancellationService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundRequestContext;
import dev.bum.ticket_service.service.payment.refund.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator.CancellationRefundCalculation;
import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator;
import dev.bum.ticket_service.service.reservation.reservation.ReservationManagementService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReservationManagementServiceTest {

    @Mock
    private TicketPurchaseCountService ticketPurchaseCountService;

    @InjectMocks
    private ReservationManagementService reservationManagementService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private ReservationRepository repository;

    @Mock
    private SeatCacheService seatCacheService;

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;

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

    @Mock
    private ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    @Spy
    @InjectMocks
    private ReservationCancellationService reservationCancellationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                reservationManagementService, "reservationCancellationService", reservationCancellationService
        );
        lenient().when(paymentRefundProcessService.inspectRequest(any(), any(), anyBoolean(), any()))
                .thenReturn(PaymentRefundRequestContext.builder()
                        .previousProcessId(null)
                        .sameRequest(false)
                        .build());
        lenient().when(paymentRefundProcessService.startGatewayAttempt(any(Payment.class), any(), anyBoolean(), any(), any()))
                .thenAnswer(invocation -> {
                    Payment payment = invocation.getArgument(0);
                    List<Ticket> selected = invocation.getArgument(1);
                    boolean full = invocation.getArgument(2);
                    CancellationRefundCalculation calculation = CancellationFeeCalculator.calculate(
                            payment.getReservation(), payment,
                            payment.getReservation().getTickets().stream()
                                    .filter(ticket -> ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.PENDING_PAYMENT)
                                    .toList(),
                            selected, reservationDiscountJpaRepository.findByReservation(payment.getReservation()), full,
                            LocalDateTime.now(clock)
                    );
                    return PaymentRefundProcessGatewayAttempt.builder()
                            .paymentRefundProcessId(1L)
                            .action(calculation.refundAmount() > 0 ? RefundAction.REQUEST_GATEWAY : RefundAction.APPLY_LOCAL)
                            .calculation(calculation)
                            .build();
                });
    }

    @Test
    @DisplayName("ID로 예약을 조회한다")
    void reservation_select_by_id() {
        Reservation reservation = reservation(1L, "order-1", "user01", event());
        Ticket.builder()
                .ticketId(1L)
                .userId("user01")
                .reservation(reservation)
                .event(reservation.getEvent())
                .seat(seat(1L, reservation.getEvent(), "VIP", 1, 1))
                .status(TicketStatus.PENDING_PAYMENT)
                .build();

        given(repository.selectById(1L)).willReturn(reservation);

        ReservationResponse response = reservationManagementService.selectById(1L);

        assertThat(response.getReservationId()).isEqualTo(1L);
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getUserId()).isEqualTo("user01");
        assertThat(response.getTicketCount()).isEqualTo(1);
        then(repository).should().selectById(1L);
    }

    @Test
    @DisplayName("예매 상세 조회는 환불 이력을 함께 반환한다")
    void reservation_select_detail_with_refund_histories() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PARTIALLY_CANCELLED);
        Payment payment = cardPayment(reservation);
        Ticket ticket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.CANCELLED);
        payment.partialRefund(125000);
        PaymentRefundProcess refundProcess = PaymentRefundProcess.create(payment, List.of(ticket), 125000, false, null);
        PaymentRefundHistory refundHistory = PaymentRefundHistory.create(refundProcess, payment, List.of(ticket), 125000, false);

        given(repository.selectById(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());
        given(reservationDeliveryJpaRepository.findByReservation(reservation)).willReturn(Optional.empty());
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(paymentRefundHistoryJpaRepository.findByReservationOrderByPaymentRefundHistoryIdDesc(reservation))
                .willReturn(List.of(refundHistory));

        var response = reservationManagementService.selectDetailById(1L);

        assertThat(response.getRefundHistories()).hasSize(1);
        assertThat(response.getRefundHistories().get(0).getRefundAmount()).isEqualTo(125000);
        assertThat(response.getRefundHistories().get(0).getPaymentStatusAfter()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(response.getRefundHistories().get(0).getTickets()).hasSize(1);
        assertThat(response.getRefundHistories().get(0).getTickets().get(0).getTicketId()).isEqualTo(1L);
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
                .selectedTicketIdList(List.of(1L, 2L))
                .build();
        Ticket selectedTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PENDING_PAYMENT);

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        given(paymentJpaRepository.findByReservation(reservation))
                .willReturn(Optional.of(virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, 0)));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(selectedTicket.getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        assertThat(remainingTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(remainingTicket.getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        then(seatCacheService).should().syncAvailableSeatsAfterCommit(List.of(selectedTicket.getSeat(), remainingTicket.getSeat()));
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

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));
        willReturn(125000).given(cardPaymentRefundService).refundPartial(payment, 125000, 1L);

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(cardPaymentRefundService).should().refundPartial(payment, 125000, 1L);
        then(paymentRefundProcessService).should().completeCancellation(1L, payment, List.of(selectedTicket), 125000, false);
    }

    @Test
    @DisplayName("카드 결제 완료 예매 부분 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_card_payment_partial_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket selectedTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        then(reservationDiscountJpaRepository).should().findByReservation(reservation);
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

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));
        given(reservationDiscountJpaRepository.findByReservation(reservation))
                .willReturn(List.of(reservationDiscount(reservation, userCoupon)));
        willReturn(250000).given(cardPaymentRefundService).refundAll(payment, 1L);

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getUsedAt()).isNull();
        then(cardPaymentRefundService).should().refundAll(payment, 1L);
        then(paymentRefundProcessService).should().completeCancellation(1L, payment, List.of(firstTicket, secondTicket), 250000, true);
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
                .refundAccount(refundAccount())
                .build();
        Ticket firstTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(firstTicket, secondTicket));
        given(reservationDiscountJpaRepository.findByReservation(reservation))
                .willReturn(List.of(reservationDiscount(reservation, userCoupon)));
        willReturn(250000).given(virtualAccountPaymentRefundService).refundAll(payment, refundAccount(), 1L);

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getUsedAt()).isNull();
        then(virtualAccountPaymentRefundService).should().refundAll(payment, refundAccount(), 1L);
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        then(paymentRefundProcessService).should().completeCancellation(1L, payment, List.of(firstTicket, secondTicket), 250000, true);
    }

    @Test
    @DisplayName("무통장 결제 완료 예매 부분 취소는 선택 티켓 비율만큼 부분 환불한다")
    void refund_virtual_account_payment_before_partial_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .refundAccount(refundAccount())
                .build();
        Ticket selectedTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));
        willReturn(125000).given(virtualAccountPaymentRefundService).refundPartial(payment, 125000, refundAccount(), 1L);

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(selectedTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(virtualAccountPaymentRefundService).should().refundPartial(payment, 125000, refundAccount(), 1L);
        then(paymentRefundProcessService).should().completeCancellation(1L, payment, List.of(selectedTicket), 125000, false);
    }

    @Test
    @DisplayName("무통장 결제 완료 예매 부분 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_virtual_account_payment_partial_reservation_cancel() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .refundAccount(refundAccount())
                .build();
        Ticket selectedTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);
        UserCoupon userCoupon = usedUserCoupon();

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selectedTicket, remainingTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        then(reservationDiscountJpaRepository).should().findByReservation(reservation);
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

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(remainingTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(remainingTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(cardPaymentRefundService).should().refundAll(payment, 1L);
        then(reservationDiscountJpaRepository).should(Mockito.times(2)).findByReservation(reservation);
    }

    @Test
    @DisplayName("무통장 부분 환불 이력이 있는 예매의 마지막 티켓 취소는 쿠폰을 복구하지 않는다")
    void do_not_restore_coupon_when_last_ticket_cancel_after_virtual_account_partial_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PARTIALLY_CANCELLED);
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.PARTIALLY_REFUNDED, 125000);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(2L))
                .refundAccount(refundAccount())
                .build();
        Ticket remainingTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(remainingTicket));

        reservationManagementService.cancel(1L, info);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(remainingTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        then(virtualAccountPaymentRefundService).should().refundAll(payment, refundAccount(), 1L);
        then(reservationDiscountJpaRepository).should(Mockito.times(2)).findByReservation(reservation);
    }

    @Test
    @DisplayName("이미 취소된 예매는 다시 취소할 수 없고 환불을 요청하지 않는다")
    void reject_cancelled_reservation_cancel_before_refund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.CANCELLED);
        CancelReservationRequest info = CancelReservationRequest.builder()
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, info))
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
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L))
                .build();
        Ticket cancelledTicket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.CANCELLED);
        Ticket activeTicket = ticket(2L, reservation, event(), seat(2L, event(), "VIP", 1, 2), TicketStatus.PAID);

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(cancelledTicket, activeTicket));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, info))
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
                .userId("user01")
                .eventId(1L)
                .selectedTicketIdList(List.of(1L, 1L))
                .build();
        Ticket ticket = ticket(1L, reservation, event(), seat(1L, event(), "VIP", 1, 1), TicketStatus.PAID);

        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, info))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("취소할 티켓이 중복 선택되었습니다.");

        then(paymentJpaRepository).shouldHaveNoInteractions();
        then(cardPaymentRefundService).shouldHaveNoInteractions();
        then(virtualAccountPaymentRefundService).shouldHaveNoInteractions();
    }


    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"PAID", "PENDING_PAYMENT"})
    void cancellationRejectsMissingPayment(TicketStatus ticketStatus) {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ticketStatus == TicketStatus.PAID ? ReservationStatus.PAID : ReservationStatus.PENDING_PAYMENT);
        Ticket ticket = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), ticketStatus);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예매의 결제 정보를 찾을 수 없습니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        assertThat(ticket.getStatus()).isEqualTo(ticketStatus);
        assertThat(ticket.getSeat().getStatus()).isEqualTo(ticketStatus == TicketStatus.PAID ? SeatStatus.RESERVED : SeatStatus.LOCKED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"READY", "WAITING_DEPOSIT", "FAILED", "CANCELLED", "EXPIRED", "REFUNDED"})
    void paidCancellationRejectsInconsistentPaymentStatus(PaymentStatus status) {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PAID);
        Ticket ticket = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PAID);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(cardPayment(reservation, status, 0)));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 완료 티켓과 결제 상태가 일치하지 않습니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(ticket.getSeat().getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    void unpaidBankCancellationDoesNotRequireRefund() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PENDING_PAYMENT);
        Ticket ticket = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));
        given(paymentJpaRepository.findByReservation(reservation))
                .willReturn(Optional.of(virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, 0)));

        var response = reservationManagementService.cancel(1L, request);

        assertThat(response.reservationId()).isEqualTo(1L);
        assertThat(response.paymentRefundProcessId()).isNull();
        assertThat(response.previousRequestRecovered()).isFalse();
        assertThat(response.cancelledTicketIds()).containsExactly(1L);
        assertThat(response.message()).isEqualTo("입금 전 예매 취소가 완료되었습니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticket.getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void unpaidBankCancellationStillRejectsAfterDailyCutoff() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PENDING_PAYMENT);
        Ticket ticket = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T14:51:00Z"));
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));
        given(paymentJpaRepository.findByReservation(reservation))
                .willReturn(Optional.of(virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, 0)));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신규 예매 취소는 매일 23시 50분 00초까지 가능합니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(ticket.getSeat().getStatus()).isEqualTo(SeatStatus.LOCKED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"READY", "FAILED", "PAID"})
    void unpaidCardCancellationIsRejected(PaymentStatus status) {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PENDING_PAYMENT);
        Ticket ticket = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(cardPayment(reservation, status, 0)));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예매 상태와 티켓의 결제 상태가 일치하지 않습니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        assertThat(ticket.getSeat().getStatus()).isEqualTo(SeatStatus.LOCKED);
    }


    @Test
    void unpaidBankCancellationRejectsPartialSelection() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PENDING_PAYMENT);
        Ticket selected = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        Ticket remaining = ticket(2L, reservation, reservation.getEvent(), seat(2L, reservation.getEvent(), "VIP", 1, 2), TicketStatus.PENDING_PAYMENT);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        Payment payment = virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, 0);
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(selected, remaining));
        given(paymentJpaRepository.findByReservation(reservation)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("입금 전 무통장 예매는 전체 취소만 가능합니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(selected.getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(remaining.getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(selected.getSeat().getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(remaining.getSeat().getStatus()).isEqualTo(SeatStatus.LOCKED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"READY", "FAILED", "PAID", "CANCELLED", "EXPIRED"})
    void unpaidBankCancellationRequiresWaitingDeposit(PaymentStatus status) {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PENDING_PAYMENT);
        Ticket ticket = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));
        given(paymentJpaRepository.findByReservation(reservation))
                .willReturn(Optional.of(virtualAccountPayment(reservation, status, 0)));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 대기 티켓과 결제 상태가 일치하지 않습니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(ticket.getSeat().getStatus()).isEqualTo(SeatStatus.LOCKED);
    }

    @Test
    void partiallyCancelledReservationRejectsUnpaidTickets() {
        Reservation reservation = reservation(1L, "order-1", "user01", event(), ReservationStatus.PARTIALLY_CANCELLED);
        Ticket ticket = ticket(1L, reservation, reservation.getEvent(), seat(1L, reservation.getEvent(), "VIP", 1, 1), TicketStatus.PENDING_PAYMENT);
        CancelReservationRequest request = CancelReservationRequest.builder().selectedTicketIdList(List.of(1L)).build();
        given(repository.selectByIdForUpdate(1L)).willReturn(reservation);
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(ticket));
        given(paymentJpaRepository.findByReservation(reservation))
                .willReturn(Optional.of(virtualAccountPayment(reservation, PaymentStatus.WAITING_DEPOSIT, 0)));

        assertThatThrownBy(() -> reservationManagementService.cancel(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예매 상태와 티켓의 결제 상태가 일치하지 않습니다.");

        then(paymentRefundProcessService).should(never()).startGatewayAttempt(any(), any(), anyBoolean(), any(), any());
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        then(seatCacheService).shouldHaveNoInteractions();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(ticket.getSeat().getStatus()).isEqualTo(SeatStatus.LOCKED);
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

    private Ticket ticket(Long ticketId, Reservation reservation, Event event, Seat seat, TicketStatus status) {
        if (status == TicketStatus.PENDING_PAYMENT) seat.lock();
        else if (status == TicketStatus.PAID) seat.reserved();
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
                .eventDateTime(LocalDateTime.of(2099, 9, 18, 18, 0))
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
