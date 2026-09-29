package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttempt;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttemptJpaRepository;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.service.checkout.payment.CheckoutPaymentService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CheckoutService {

    private static final DateTimeFormatter PAYMENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final SeatCacheService seatCacheService;
    private final QueueAccessService queueAccessService;
    private final ReservationRepository reservationRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final CheckoutAttemptJpaRepository checkoutAttemptJpaRepository;
    private final CheckoutPaymentService checkoutPaymentService;
    private final PaymentGatewayCardClient paymentGatewayCardClient;
    private final CheckoutIdempotencyKeyGenerator idempotencyKeyGenerator;

    @Value("${payment.expiration.ready-timeout-minutes:10}")
    private long paymentReadyTimeoutMinutes = 10;

    @Value("${app.checkout.reservation-fee-per-ticket:4000}")
    private int reservationFeePerTicket = 4000;

    @Value("${app.checkout.delivery-fee:3200}")
    private int deliveryFee = 3200;

    /**
     * 좌석 선택 완료 후 배송/결제 정보 입력 화면으로 이동할 수 있는지 검증한다.
     * active token과 Redis 좌석 선점 상태가 유효한지 검증한다.
     */
    @AuditLog(action = "CHECKOUT_PREPARE", targetType = "CHECKOUT")
    public CheckoutPrepareResponse prepare(String currentUserId, String activeToken, CheckoutPrepareRequest request) {
        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);

        LocalDateTime expiresAt = seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                request.getSeats()
        );

        LocalDateTime preparedAt = LocalDateTime.now();
        CheckoutAttempt checkoutAttempt = CheckoutAttempt.prepare(
                idempotencyKeyGenerator.generate(),
                currentUserId,
                request.getOrderId(),
                request.getEventId(),
                generatePaymentNo(),
                expiresAt
        );
        checkoutAttemptJpaRepository.save(checkoutAttempt);

        return CheckoutPrepareResponse.builder()
                .eventId(request.getEventId())
                .orderId(request.getOrderId())
                .seats(request.getSeats())
                .idempotencyKey(checkoutAttempt.getIdempotencyKey())
                .prepared(true)
                .preparedAt(preparedAt)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 배송/쿠폰/결제수단 입력 완료 후 예약, 배송, 결제 정보를 생성한다.
     * 무통장 결제는 이 단계에서 가상계좌까지 발급하고, 카드 결제는 PG 승인 전 READY 상태로 반환한다.
     */
    @AuditLog(action = "CHECKOUT_CONFIRM", targetType = "CHECKOUT")
    public PaymentResponse confirm(String currentUserId, String activeToken, CheckoutConfirmRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        CheckoutAttempt checkoutAttempt = checkoutAttemptJpaRepository
                .findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 결제 멱등 키입니다."));

        validateRequestMatchesAttempt(currentUserId, request, checkoutAttempt);

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

        seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                request.getSeats()
        );

        Reservation reservation = reservationRepository.insert(toReservationRequest(currentUserId, request));
        if (request.getDelivery() != null) {
            reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, request.getDelivery()));
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
                .amount(paymentAmount)
                .reservationFeeAmount(reservationFeePerTicket * reservation.getTickets().size())
                .deliveryFeeAmount(request.getDelivery() != null ? deliveryFee : 0)
                .idempotencyKey(idempotencyKey)
                .requestedAt(requestedAt)
                .expiresAt(requestedAt.plusMinutes(paymentReadyTimeoutMinutes))
                .build();

        checkoutPaymentService.process(request, payment);

        Payment savedPayment = paymentJpaRepository.save(payment);
        checkoutAttempt.confirm(savedPayment);

        return savedPayment.toResponse();
    }

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
    }

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

        return switchCardToBankTransfer(currentUserId, activeToken, request, checkoutAttempt, confirmedPayment);
    }

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

        Payment payment = paymentJpaRepository.findByPaymentNoForUpdate(confirmedPayment.getPaymentNo())
                .orElseThrow(() -> new IllegalStateException("확정된 checkout의 결제 정보를 찾을 수 없습니다."));

        if (payment.getMethod() != PaymentMethod.CREDIT_CARD || payment.getStatus() != PaymentStatus.READY) {
            throw new IllegalStateException("무통장 결제로 변경할 수 없는 결제 상태입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (checkoutAttempt.isExpired(now) || payment.getExpiresAt() == null
                || !payment.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException("좌석 선점 시간이 만료되었습니다.");
        }

        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);
        seatCacheService.validateOccupiedSeat(
                request.getEventId(), currentUserId, request.getOrderId(), request.getSeats()
        );

        if (!Boolean.FALSE.equals(paymentGatewayCardClient.hasApprovalHistory(payment.getPaymentNo()))) {
            throw new IllegalStateException("카드 승인 이력이 있어 결제 수단을 변경할 수 없습니다.");
        }

        payment.switchToBankTransfer();
        checkoutPaymentService.process(request, payment);
        return payment.toResponse();
    }


    private InsertReservationRequest toReservationRequest(String currentUserId, CheckoutConfirmRequest request) {
        return InsertReservationRequest.builder()
                .orderId(request.getOrderId())
                .userId(currentUserId)
                .eventId(request.getEventId())
                .seats(request.getSeats())
                .userCouponId(request.getUserCouponId())
                .build();
    }

    private int calculateTotalTicketAmount(Reservation reservation) {
        return reservation.getTickets().stream()
                .mapToInt(Ticket::getPrice)
                .sum();
    }

    private int calculateDiscountAmount(Reservation reservation) {
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);
        return discounts.stream()
                .mapToInt(ReservationDiscount::getDiscountAmount)
                .sum();
    }

    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_FORMATTER);
        String randomValue = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "PAY-" + timestamp + "-" + randomValue;
    }

    /**
     * confirm 요청의 idempotencyKey는 prepare 응답으로 내려준 값을 필수로 받고, 앞뒤 공백을 제거해 저장/조회 기준을 고정한다.
     */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("결제 멱등 키가 필요합니다.");
        }

        return idempotencyKey.trim();
    }

}
