package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 입금/만료 통지도 취소와 같은 예약 → 결제 순서로 잠근다. */
@Service
@RequiredArgsConstructor
public class VirtualAccountPaymentLockService {
    private final PaymentJpaRepository payments;
    private final ReservationJpaRepository reservations;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public Payment lock(String paymentNo) {
        // 엔티티를 미리 읽지 않아 잠금 대기 전의 상태가 영속성 컨텍스트에 남지 않게 한다.
        Long reservationId = payments.findReservationIdByPaymentNo(paymentNo)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예매 정보를 찾을 수 없습니다."));
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        Payment payment = payments.findByPaymentNoForUpdate(paymentNo)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        return payment;
    }
}
