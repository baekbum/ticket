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
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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

    // 결제 번호에 포함할 날짜와 시각 표시 형식.
    private static final DateTimeFormatter PAYMENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // 좌석 캐시 업무 처리를 담당하는 서비스.
    private final SeatCacheService seatCacheService;
    // 공연 예매 변경의 동시성과 처리 조건을 확인하는 도구.
    private final EventBookingGuard eventBookingGuard;
    // 좌석 데이터를 조회하고 저장하는 레포지토리.
    private final SeatJpaRepository seatJpaRepository;
    // 대기열 업무 처리를 담당하는 서비스.
    private final QueueAccessService queueAccessService;
    // 예매 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationRepository reservationRepository;
    // 예매 배송 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    // 예매 할인 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    // 결제 데이터를 조회하고 저장하는 레포지토리.
    private final PaymentJpaRepository paymentJpaRepository;
    // 결제 준비 데이터를 조회하고 저장하는 레포지토리.
    private final CheckoutAttemptJpaRepository checkoutAttemptJpaRepository;
    // 결제 준비 업무 처리를 담당하는 서비스.
    private final CheckoutPaymentService checkoutPaymentService;
    // 결제 게이트웨이 연동 API를 호출하는 클라이언트.
    private final PaymentGatewayCardClient paymentGatewayCardClient;
    // 결제 준비 요청의 중복 처리를 방지하는 키 생성 도구.
    private final CheckoutIdempotencyKeyGenerator idempotencyKeyGenerator;
    // 티켓 한 장당 예매 수수료.
    @Value("${app.checkout.reservation-fee-per-ticket:4000}")
    private int reservationFeePerTicket = 4000;
    // 배송비.
    @Value("${app.checkout.delivery-fee:3200}")
    private int deliveryFee = 3200;

    /**
     * 배송/쿠폰/결제수단 입력 완료 후 예약, 배송, 결제 정보를 생성한다. 무통장 결제는 이 단계에서 가상계좌까지 발급하고, 카드 결제는 PG 승인 전 READY 상태로
     * 반환한다.
     */
    @AuditLog(action = "CHECKOUT_CONFIRM", targetType = "CHECKOUT")
    public PaymentResponse confirm(
            String currentUserId,
            String activeToken,
            CheckoutConfirmRequest request
    ) {

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

    /** 좌석 선택 완료 후 배송/결제 정보 입력 화면으로 이동할 수 있는지 검증한다. active token과 Redis 좌석 선점 상태가 유효한지 검증한다. */
    @AuditLog(action = "CHECKOUT_PREPARE", targetType = "CHECKOUT")
    public CheckoutPrepareResponse prepare(
            String currentUserId,
            String activeToken,
            CheckoutPrepareRequest request
    ) {

        if (!StringUtils.hasText(request.getOrderId())) {
            throw new IllegalArgumentException("주문 번호가 필요합니다.");
        }

        Set<Long> requestedSeatIds = seatIds(request.getSeats());
        List<SeatInfo> seats = validateSeatsFromDatabase(request.getEventId(), request.getSeats()).stream()
                .sorted(Comparator.comparingLong(SeatInfo::getId))
                .toList();

        // INSERT에 필요한 만료 시각을 확인하고, 잠금을 얻은 뒤 선점 소유권을 다시 검증한다.
        LocalDateTime expiresAt = seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                seats
        );
        if (expiresAt != null) expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);

        CheckoutAttempt candidate = CheckoutAttempt.prepare(
                idempotencyKeyGenerator.generate(),
                currentUserId,
                request.getOrderId(),
                request.getEventId(),
                requestedSeatIds,
                generatePaymentNo(),
                expiresAt
        );

        checkoutAttemptJpaRepository.insertIfAbsent(candidate);

        CheckoutAttempt checkoutAttempt = checkoutAttemptJpaRepository.findByOrderIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new IllegalStateException("checkout 준비 정보를 찾을 수 없습니다."));

        validatePrepareMatchesAttempt(currentUserId, request, requestedSeatIds, checkoutAttempt);
        validatePreparedAttempt(checkoutAttempt);
        Event event = eventBookingGuard.requireBookable(request.getEventId(), true);
        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);
        seatCacheService.validateOccupiedSeat(request.getEventId(), currentUserId, request.getOrderId(), seats);
        validatePreparedAttempt(checkoutAttempt);
        eventBookingGuard.validate(event, true);

        return CheckoutPrepareResponse.builder()
                .eventId(checkoutAttempt.getEventId())
                .orderId(checkoutAttempt.getOrderId())
                .seats(seats)
                .idempotencyKey(checkoutAttempt.getIdempotencyKey())
                .prepared(true)
                .preparedAt(checkoutAttempt.getCreatedAt())
                .expiresAt(checkoutAttempt.getExpiresAt())
                .build();
    }

    /**
     * 결제 준비 요청과 기존 시도 정보가 일치하는지 검증한다.
     */
    private void validatePrepareMatchesAttempt(
            String currentUserId,
            CheckoutPrepareRequest request,
            Set<Long> requestedSeatIds,
            CheckoutAttempt attempt
    ) {

        if (!currentUserId.equals(attempt.getUserId())) {
            throw new AccessDeniedException("다른 사용자의 checkout 준비 정보입니다.");
        }

        if (!attempt.getOrderId().equals(request.getOrderId())
                || !attempt.getEventId().equals(request.getEventId())
                || !attempt.getSeatIds().equals(requestedSeatIds)) {
            throw new IllegalArgumentException("기존 checkout 준비 정보와 요청이 일치하지 않습니다.");
        }
    }

    /**
     * 결제 준비 시도가 실행 가능한 상태인지 검증한다.
     */
    private void validatePreparedAttempt(CheckoutAttempt attempt) {

        if (!attempt.isPrepared()) {
            throw new IllegalStateException("결제를 진행할 수 없는 checkout 준비 상태입니다.");
        }

        if (attempt.isExpired(LocalDateTime.now())) {
            throw new IllegalStateException("좌석 선점 시간이 만료되었습니다.");
        }
    }

    /**
     * 현재 요청과 기존 결제 시도가 일치하는지 검증한다.
     */
    private void validateRequestMatchesAttempt(
            String currentUserId,
            CheckoutConfirmRequest request,
            CheckoutAttempt checkoutAttempt
    ) {

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

    /**
     * 이미 확정된 결제의 중복 요청을 처리한다.
     */
    private PaymentResponse handleConfirmedPayment(
            String currentUserId,
            String activeToken,
            CheckoutConfirmRequest request,
            CheckoutAttempt checkoutAttempt
    ) {

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

    /**
     * 카드 결제 요청을 가상계좌 결제 요청으로 전환한다.
     */
    private PaymentResponse switchCardToBankTransfer(
            String currentUserId,
            String activeToken,
            CheckoutConfirmRequest request,
            CheckoutAttempt checkoutAttempt,
            Payment confirmedPayment
    ) {

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

    /**
     * 결제 준비 정보를 예매 생성 요청으로 변환한다.
     */
    private InsertReservationRequest toReservationRequest(
            String currentUserId,
            CheckoutConfirmRequest request,
            List<SeatInfo> seats
    ) {

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

    /**
     * 선택한 좌석 식별자 목록을 구성한다.
     */
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

    /**
     * 선택한 티켓의 총 금액을 계산한다.
     */
    private int calculateTotalTicketAmount(Reservation reservation) {

        return reservation.getTickets().stream().mapToInt(Ticket::getPrice).sum();
    }

    /**
     * 적용할 쿠폰 할인 금액을 계산한다.
     */
    private int calculateDiscountAmount(Reservation reservation) {

        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);
        return discounts.stream().mapToInt(ReservationDiscount::getDiscountAmount).sum();
    }

    /**
     * 결제를 식별할 결제 번호를 생성한다.
     */
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
