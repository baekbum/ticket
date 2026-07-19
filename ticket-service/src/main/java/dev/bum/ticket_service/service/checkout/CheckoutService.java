package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
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
    private final ReservationRepository reservationRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;

    /**
     * 결제 버튼 클릭 시 호출되는 결제 준비 로직.
     * Redis 좌석 선점을 검증한 뒤 예약, 할인, 배송, 결제 정보를 하나의 트랜잭션으로 생성한다.
     */
    public CheckoutPrepareResponse prepare(String currentUserId, CheckoutPrepareRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        CheckoutPrepareResponse existingResponse = findExistingResponse(currentUserId, idempotencyKey);
        if (existingResponse != null) {
            return existingResponse;
        }

        InsertReservationRequest reservationRequest = toReservationRequest(currentUserId, request);
        seatCacheService.validateOccupiedSeat(reservationRequest);

        Reservation reservation = reservationRepository.insert(reservationRequest);
        reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, request.getDelivery()));

        int totalTicketAmount = calculateTotalTicketAmount(reservation);
        int discountAmount = calculateDiscountAmount(reservation);
        int paymentAmount = totalTicketAmount - discountAmount;

        Payment payment = paymentJpaRepository.save(Payment.builder()
                .reservation(reservation)
                .paymentNo(generatePaymentNo())
                .method(request.getPaymentMethod())
                .status(PaymentStatus.READY)
                .amount(paymentAmount)
                .idempotencyKey(idempotencyKey)
                .depositorName(request.getDepositorName())
                .requestedAt(LocalDateTime.now())
                .build());

        seatCacheService.updateUserPurchaseLimit(
                request.getEventId(),
                currentUserId,
                request.getSeats().size(),
                "PLUS"
        );

        return toPrepareResponse(payment, reservation, totalTicketAmount, discountAmount);
    }

    /**
     * checkout 요청을 기존 예약 생성 로직에서 사용하는 요청 형태로 변환한다.
     */
    private InsertReservationRequest toReservationRequest(String currentUserId, CheckoutPrepareRequest request) {
        return InsertReservationRequest.builder()
                .orderId(request.getOrderId())
                .userId(currentUserId)
                .eventId(request.getEventId())
                .seats(request.getSeats())
                .userCouponId(request.getUserCouponId())
                .build();
    }

    /**
     * 같은 idempotencyKey로 이미 생성된 결제 준비 결과가 있으면 기존 응답을 반환한다.
     * 다른 사용자의 키를 재사용하는 요청은 차단한다.
     */
    private CheckoutPrepareResponse findExistingResponse(String currentUserId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }

        return paymentJpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> {
                    Reservation reservation = payment.getReservation();
                    if (!currentUserId.equals(reservation.getUserId())) {
                        throw new AccessDeniedException("다른 사용자의 결제 요청 키입니다.");
                    }

                    int totalTicketAmount = calculateTotalTicketAmount(reservation);
                    int discountAmount = calculateDiscountAmount(reservation);
                    return toPrepareResponse(payment, reservation, totalTicketAmount, discountAmount);
                })
                .orElse(null);
    }

    /**
     * 예약에 포함된 티켓 가격을 합산해 할인 전 총 티켓 금액을 계산한다.
     */
    private int calculateTotalTicketAmount(Reservation reservation) {
        return reservation.getTickets().stream()
                .mapToInt(Ticket::getPrice)
                .sum();
    }

    /**
     * 예약에 적용된 모든 할인 기록을 합산해 최종 할인 금액을 계산한다.
     */
    private int calculateDiscountAmount(Reservation reservation) {
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);
        return discounts.stream()
                .mapToInt(ReservationDiscount::getDiscountAmount)
                .sum();
    }

    /**
     * 결제 준비 결과를 프론트가 PG 결제 시도에 사용할 응답 DTO로 변환한다.
     */
    private CheckoutPrepareResponse toPrepareResponse(Payment payment, Reservation reservation, int totalTicketAmount, int discountAmount) {
        return CheckoutPrepareResponse.builder()
                .reservationId(reservation.getReservationId())
                .orderId(reservation.getOrderId())
                .paymentId(payment.getPaymentId())
                .paymentNo(payment.getPaymentNo())
                .paymentMethod(payment.getMethod())
                .paymentStatus(payment.getStatus())
                .totalTicketAmount(totalTicketAmount)
                .discountAmount(discountAmount)
                .amount(payment.getAmount())
                .build();
    }

    /**
     * 빈 문자열 idempotencyKey는 중복 방지 키로 쓰지 않도록 null로 정규화한다.
     */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        return StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
    }

    /**
     * 결제 요청을 식별할 내부 결제번호를 생성한다.
     */
    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_FORMATTER);
        String randomValue = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "PAY-" + timestamp + "-" + randomValue;
    }
}
