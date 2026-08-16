package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.service.payment.MockVirtualAccountIssueService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private static final int MAX_ACCOUNT_ISSUE_ATTEMPTS = 5;

    private final SeatCacheService seatCacheService;
    private final QueueAccessService queueAccessService;
    private final ReservationRepository reservationRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final MockVirtualAccountIssueService mockVirtualAccountIssueService;

    @Value("${payment.expiration.ready-timeout-minutes:10}")
    private long paymentReadyTimeoutMinutes = 10;

    /**
     * 좌석 선택 완료 후 배송/결제 정보 입력 화면으로 이동할 수 있는지 검증한다.
     * active token과 Redis 좌석 선점 상태가 유효하면 active token을 회수해 다음 대기자가 입장할 수 있게 한다.
     */
    @AuditLog(action = "CHECKOUT_PREPARE", targetType = "CHECKOUT")
    public CheckoutPrepareResponse prepare(String currentUserId, String activeToken, CheckoutPrepareRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());

        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);

        seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                request.getSeats()
        );

        releaseActiveTokenAfterCommit(request.getEventId(), currentUserId, activeToken);

        return CheckoutPrepareResponse.builder()
                .eventId(request.getEventId())
                .orderId(request.getOrderId())
                .seats(request.getSeats())
                .idempotencyKey(idempotencyKey)
                .prepared(true)
                .preparedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 배송/쿠폰/결제수단 입력 완료 후 예약, 배송, 결제 정보를 생성한다.
     * 무통장 결제는 이 단계에서 가상계좌까지 발급하고, 카드 결제는 PG 승인 전 READY 상태로 반환한다.
     */
    @AuditLog(action = "CHECKOUT_CONFIRM", targetType = "CHECKOUT")
    public PaymentResponse confirm(String currentUserId, CheckoutConfirmRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        Payment existingPayment = findExistingPayment(currentUserId, idempotencyKey);
        if (existingPayment != null) {
            return existingPayment.toResponse();
        }

        seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                request.getSeats()
        );

        Reservation reservation = reservationRepository.insert(toReservationRequest(currentUserId, request));
        reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, request.getDelivery()));

        int totalTicketAmount = calculateTotalTicketAmount(reservation);
        int discountAmount = calculateDiscountAmount(reservation);
        int paymentAmount = totalTicketAmount - discountAmount;
        LocalDateTime requestedAt = LocalDateTime.now();

        Payment payment = Payment.builder()
                .reservation(reservation)
                .paymentNo(generatePaymentNo())
                .method(request.getPaymentMethod())
                .status(PaymentStatus.READY)
                .amount(paymentAmount)
                .idempotencyKey(idempotencyKey)
                .requestedAt(requestedAt)
                .expiresAt(requestedAt.plusMinutes(paymentReadyTimeoutMinutes))
                .build();

        if (request.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            validateBankTransferRequest(request);
            MockVirtualAccountIssueService.VirtualAccount virtualAccount = issueUniqueVirtualAccount(request.getBankCode());
            payment.waitDeposit(
                    virtualAccount.getBankName(),
                    virtualAccount.getAccountNumber(),
                    virtualAccount.getExpiresAt()
            );
        }

        Payment savedPayment = paymentJpaRepository.save(payment);
        seatCacheService.updateUserPurchaseLimit(
                reservation.getEvent(),
                currentUserId,
                request.getSeats().size(),
                "PLUS"
        );

        return savedPayment.toResponse();
    }

    private void validateBankTransferRequest(CheckoutConfirmRequest request) {
        if (!StringUtils.hasText(request.getBankCode())) {
            throw new IllegalArgumentException("은행 코드가 필요합니다.");
        }
    }

    /**
     * checkout 준비 트랜잭션이 성공한 뒤 active token을 회수해 다음 대기자가 입장할 수 있게 한다.
     */
    private void releaseActiveTokenAfterCommit(Long eventId, String userId, String activeToken) {
        runAfterCommit(() -> queueAccessService.complete(eventId, userId, activeToken));
    }

    private Payment findExistingPayment(String currentUserId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }

        return paymentJpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> {
                    Reservation reservation = payment.getReservation();
                    if (reservation == null || !currentUserId.equals(reservation.getUserId())) {
                        throw new AccessDeniedException("다른 사용자의 결제 요청 키입니다.");
                    }
                    return payment;
                })
                .orElse(null);
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

    private MockVirtualAccountIssueService.VirtualAccount issueUniqueVirtualAccount(String bankCode) {
        for (int attempt = 0; attempt < MAX_ACCOUNT_ISSUE_ATTEMPTS; attempt++) {
            MockVirtualAccountIssueService.VirtualAccount virtualAccount = mockVirtualAccountIssueService.issue(bankCode);
            if (!paymentJpaRepository.existsByAccountNumber(virtualAccount.getAccountNumber())) {
                return virtualAccount;
            }
        }

        throw new IllegalStateException("가상계좌 번호를 발급하지 못했습니다.");
    }

    private String generatePaymentNo() {
        String timestamp = LocalDateTime.now().format(PAYMENT_NO_FORMATTER);
        String randomValue = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "PAY-" + timestamp + "-" + randomValue;
    }

    /**
     * DB 트랜잭션 커밋이 성공한 뒤에만 외부 부수 효과를 실행한다.
     * 트랜잭션이 없을 때는 호출 위치에서 즉시 실행한다.
     */
    private void runAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    /**
     * idempotencyKey는 필수로 받고, 앞뒤 공백을 제거해 저장/조회 기준을 고정한다.
     */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("결제 멱등 키가 필요합니다.");
        }

        return idempotencyKey.trim();
    }

}
