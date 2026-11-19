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

    // 결제 데이터를 조회하고 저장하는 레포지토리.
    private final PaymentJpaRepository payments;
    // 예매 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationJpaRepository reservations;
    // 엔티티의 영속 상태와 데이터베이스 잠금을 관리하는 도구.
    private final EntityManager entityManager;

    /**
     * 가상계좌 처리에서 동시 변경을 막기 위한 잠금을 획득한다.
     */
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
