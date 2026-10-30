package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationDeliveryStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttempt;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttemptJpaRepository;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.payment.payment.PaymentSnapshot;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.service.checkout.payment.CheckoutPaymentService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.service.event.event.EventBookingGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CheckoutService {

    private static final DateTimeFormatter PAYMENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final SeatCacheService seatCacheService;
    private final EventBookingGuard eventBookingGuard;
    private final SeatJpaRepository seatJpaRepository;
    private final QueueAccessService queueAccessService;
    private final ReservationRepository reservationRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final CheckoutAttemptJpaRepository checkoutAttemptJpaRepository;
    private final CheckoutPaymentService checkoutPaymentService;
    private final PaymentGatewayCardClient paymentGatewayCardClient;
    private final CheckoutIdempotencyKeyGenerator idempotencyKeyGenerator;

    @Value("${app.checkout.reservation-fee-per-ticket:4000}")
    private int reservationFeePerTicket = 4000;

    @Value("${app.checkout.delivery-fee:3200}")
    private int deliveryFee = 3200;

    /** 좌석 선택 완료 후 배송/결제 정보 입력 화면으로 이동할 수 있는지 검증한다. active token과 Redis 좌석 선점 상태가 유효한지 검증한다. */
    @AuditLog(action = "CHECKOUT_PREPARE", targetType = "CHECKOUT")
    public CheckoutPrepareResponse prepare(
            String currentUserId, String activeToken, CheckoutPrepareRequest request) {

        Event event = eventBookingGuard.requireBookable(request.getEventId(), true);
        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);
        List<SeatInfo> seats = validateSeatsFromDatabase(request.getEventId(), request.getSeats());
        LocalDateTime expiresAt = seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                seats
        );

        eventBookingGuard.validate(event, true);
        LocalDateTime preparedAt = LocalDateTime.now();
        CheckoutAttempt checkoutAttempt = CheckoutAttempt.prepare(
                idempotencyKeyGenerator.generate(),
                currentUserId,
                request.getOrderId(),
                request.getEventId(),
                seatIds(seats),
                generatePaymentNo(),
                expiresAt
        );
        checkoutAttemptJpaRepository.save(checkoutAttempt);

        return CheckoutPrepareResponse.builder()
                .eventId(request.getEventId())
                .orderId(request.getOrderId())
                .seats(seats)
                .idempotencyKey(checkoutAttempt.getIdempotencyKey())
                .prepared(true)
                .preparedAt(preparedAt)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 배송/쿠폰/결제수단 입력 완료 후 예약, 배송, 결제 정보를 생성한다. 무통장 결제는 이 단계에서 가상계좌까지 발급하고, 카드 결제는 PG 승인 전 READY 상태로
     * 반환한다.
     */
    @AuditLog(action = "CHECKOUT_CONFIRM", targetType = "CHECKOUT")
    public PaymentResponse confirm(
            String currentUserId, String activeToken, CheckoutConfirmRequest request) {

        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        CheckoutAttempt checkoutAttempt = checkoutAttemptJpaRepository
                .findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제 멱등 키입니다."));

        validateRequestMatchesAttempt(currentUserId, request, checkoutAttempt);

        // 선점·대기열 통과 이력이나 기존 멱등 결과가 판매 종료 검증을 우회하지 못한다.
        Event event = eventBookingGuard.requireBookable(request.getEventId(), true);

        if (checkoutAttempt.isConfirmed()) {
            return handleConfirmedPayment(currentUserId, activeToken, request, checkoutAttempt);
        }

        if (!checkoutAttempt.isPrepared()) {
            throw new IllegalStateException("결제를 진행할 수 없는 checkout 상태입니다.");
        }

        if (checkoutAttempt.isExpired(LocalDateTime.now())) {
            throw new IllegalStateException("좌석 선점 시간이 만료되었습니다.");
        }

        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);

        List<SeatInfo> seats = validateSeatsFromDatabase(request.getEventId(), request.getSeats());
        LocalDateTime seatExpiresAt = seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                seats
        );

        eventBookingGuard.validate(event, true);
        Reservation reservation = reservationRepository.insert(toReservationRequest(currentUserId, request, seats));

        if (request.getDelivery() != null) {
            ReservationDelivery delivery = ReservationDelivery.builder()
                    .reservation(reservation)
                    .recipientName(request.getDelivery().getRecipientName())
                    .recipientPhone(request.getDelivery().getRecipientPhone())
                    .zipCode(request.getDelivery().getZipCode())
                    .address(request.getDelivery().getAddress())
                    .detailAddress(request.getDelivery().getDetailAddress())
                    .deliveryMessage(request.getDelivery().getDeliveryMessage())
                    .status(ReservationDeliveryStatus.READY)
                    .build();
            reservationDeliveryJpaRepository.save(delivery);
        }

        int totalTicketAmount = calculateTotalTicketAmount(reservation);
        int discountAmount = calculateDiscountAmount(reservation);
        int paymentAmount = Math.max(0, totalTicketAmount - discountAmount)
                + reservationFeePerTicket * reservation.getTickets().size()
                + (request.getDelivery() != null ? deliveryFee : 0);
        LocalDateTime requestedAt = LocalDateTime.now();

        Payment payment = Payment.builder()
                .reservation(reservation)
                .paymentNo(checkoutAttempt.getPaymentNo())
                .method(request.getPaymentMethod())
                .status(PaymentStatus.READY)
                .snapshot(
                        PaymentSnapshot.builder()
                                .amount(paymentAmount)
                                .reservationFeeAmount(reservationFeePerTicket * reservation.getTickets().size())
                                .deliveryFeeAmount(request.getDelivery() != null ? deliveryFee : 0)
                                .tickets(reservation.getTickets())
                                .discounts(reservationDiscountJpaRepository.findByReservation(reservation))
                                .build()
                )
                .idempotencyKey(idempotencyKey)
                .requestedAt(requestedAt)
                .expiresAt(seatExpiresAt)
                .build();

        checkoutPaymentService.process(request, payment);

        Payment savedPayment = paymentJpaRepository.save(payment);
        checkoutAttempt.confirm(savedPayment);

        return savedPayment.toResponse();
    }

    private void validateRequestMatchesAttempt(
            String currentUserId, CheckoutConfirmRequest request, CheckoutAttempt checkoutAttempt) {

        if (!currentUserId.equals(checkoutAttempt.getUserId())) {
            throw new AccessDeniedException("다른 사용자의 결제 요청 키입니다.");
        }

        if (!checkoutAttempt.getOrderId().equals(request.getOrderId())
                || !checkoutAttempt.getEventId().equals(request.getEventId())) {
            throw new IllegalArgumentException("prepare 요청과 결제 확정 정보가 일치하지 않습니다.");
        }

        // 재요청과 결제수단 전환도 최초 prepare에서 검증한 좌석을 유지해야 한다.
        if (checkoutAttempt.getSeatIds().isEmpty()) {
            throw new IllegalArgumentException("좌석 준비 정보가 없습니다. 좌석 선택부터 다시 진행해주세요.");
        }
        if (!checkoutAttempt.getSeatIds().equals(seatIds(request.getSeats()))) {
            throw new IllegalArgumentException("prepare 요청과 결제 확정 좌석이 일치하지 않습니다.");
        }
    }

    private PaymentResponse handleConfirmedPayment(
            String currentUserId,
            String activeToken,
            CheckoutConfirmRequest request,
            CheckoutAttempt checkoutAttempt) {
        Payment confirmedPayment = checkoutAttempt.getPayment();

        if (confirmedPayment == null) {
            throw new IllegalStateException("확정된 checkout의 결제 정보를 찾을 수 없습니다.");
        }

        if (confirmedPayment.getMethod() == request.getPaymentMethod()) {
            return confirmedPayment.toResponse();
        }

        return switchCardToBankTransfer(
                currentUserId, activeToken, request, checkoutAttempt, confirmedPayment);
    }

    private PaymentResponse switchCardToBankTransfer(
            String currentUserId,
            String activeToken,
            CheckoutConfirmRequest request,
            CheckoutAttempt checkoutAttempt,
            Payment confirmedPayment) {

        if (confirmedPayment.getMethod() != PaymentMethod.CREDIT_CARD
                || request.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalStateException("변경할 수 없는 결제 수단입니다.");
        }

        Payment payment = paymentJpaRepository
                .findByPaymentNoForUpdate(confirmedPayment.getPaymentNo())
                .orElseThrow(
                        () -> new IllegalStateException("확정된 checkout의 결제 정보를 찾을 수 없습니다.")
                );

        if (payment.getMethod() != PaymentMethod.CREDIT_CARD
                || payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalStateException("무통장 결제로 변경할 수 없는 결제 상태입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (checkoutAttempt.isExpired(now)
                || payment.getExpiresAt() == null
                || !payment.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException("좌석 선점 시간이 만료되었습니다.");
        }

        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);
        eventBookingGuard.requireBookable(request.getEventId(), true);
        List<SeatInfo> seats = validateSeatsFromDatabase(request.getEventId(), request.getSeats());
        seatCacheService.validateOccupiedSeat(
                request.getEventId(), currentUserId, request.getOrderId(), seats);

        if (!Boolean.FALSE.equals(
                paymentGatewayCardClient.hasApprovalHistory(payment.getPaymentNo()))) {
            throw new IllegalStateException("카드 승인 이력이 있어 결제 수단을 변경할 수 없습니다.");
        }

        payment.switchToBankTransfer();
        checkoutPaymentService.process(request, payment);
        return payment.toResponse();
    }

    private InsertReservationRequest toReservationRequest(
            String currentUserId, CheckoutConfirmRequest request, List<SeatInfo> seats) {

        return InsertReservationRequest.builder()
                .orderId(request.getOrderId())
                .userId(currentUserId)
                .eventId(request.getEventId())
                .seats(seats)
                .userCouponId(request.getUserCouponId())
                .build();
    }

    /** 요청의 ID와 좌표를 검증하고 Redis 검증과 예약 생성에는 DB의 좌표만 전달한다. */
    private List<SeatInfo> validateSeatsFromDatabase(Long eventId, List<SeatInfo> requestedSeats) {
        Set<Long> ids = seatIds(requestedSeats);
        Map<Long, Seat> seats = seatJpaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Seat::getSeatId, Function.identity()));

        if (eventId == null || seats.size() != ids.size()) {
            throw new IllegalArgumentException("요청한 좌석을 찾을 수 없습니다.");
        }

        return requestedSeats.stream().map(info -> {
            Seat seat = seats.get(info.getId());
            if (seat.getEvent() == null || !eventId.equals(seat.getEvent().getEventId())
                    || !Objects.equals(seat.getZone(), info.getZone())
                    || !Objects.equals(seat.getSeatRow(), info.getRow())
                    || !Objects.equals(seat.getSeatCol(), info.getCol())) {
                throw new IllegalArgumentException("좌석 ID와 공연 또는 좌표 정보가 일치하지 않습니다.");
            }
            return SeatInfo.builder()
                    .id(seat.getSeatId()).zone(seat.getZone())
                    .row(seat.getSeatRow()).col(seat.getSeatCol()).build();
        }).toList();
    }

    private Set<Long> seatIds(List<SeatInfo> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("좌석을 한 개 이상 선택해야 합니다.");
        }
        Set<Long> ids = new HashSet<>();
        for (SeatInfo seat : seats) {
            if (seat == null || seat.getId() <= 0 || !ids.add(seat.getId())) {
                throw new IllegalArgumentException("좌석 ID가 올바르지 않거나 중복되었습니다.");
            }
        }
        return ids;
    }

    private int calculateTotalTicketAmount(Reservation reservation) {
        return reservation.getTickets().stream().mapToInt(Ticket::getPrice).sum();
    }

    private int calculateDiscountAmount(Reservation reservation) {
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);
        return discounts.stream().mapToInt(ReservationDiscount::getDiscountAmount).sum();
    }

    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_FORMATTER);
        String randomValue = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "PAY-" + timestamp + "-" + randomValue;
    }

    /** confirm 요청의 idempotencyKey는 prepare 응답으로 내려준 값을 필수로 받고, 앞뒤 공백을 제거해 저장/조회 기준을 고정한다. */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("결제 멱등 키가 필요합니다.");
        }

        return idempotencyKey.trim();
    }
}
