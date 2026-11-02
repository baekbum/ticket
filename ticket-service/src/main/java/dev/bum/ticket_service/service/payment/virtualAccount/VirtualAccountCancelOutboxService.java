package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class VirtualAccountCancelOutboxService {
    private final VirtualAccountCancelOutboxJpaRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Payment payment) {
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER || payment.getStatus() != PaymentStatus.CANCELLED
                || payment.getReservation() == null || payment.getReservation().getStatus() != ReservationStatus.CANCELLED
                || !StringUtils.hasText(payment.getPaymentNo()) || !StringUtils.hasText(payment.getAccountNumber())
                || !StringUtils.hasText(payment.getBankName()) || payment.getAmount() == null) {
            throw new IllegalStateException("취소된 무통장 결제와 계좌 정보가 필요합니다.");
        }
        // 호출자가 결제를 잠근 트랜잭션에서 저장한다. 전용 테이블의 paymentNo가 취소 작업의 멱등 기준이다.
        VirtualAccountCancelOutbox existing = repository.findByPaymentNo(payment.getPaymentNo()).orElse(null);
        VirtualAccountCancelOutbox pending = VirtualAccountCancelOutbox.pending(payment);
        if (existing == null) repository.save(pending);
        else if (!existing.toRequest().equals(pending.toRequest())) {
            throw new IllegalStateException("같은 결제의 계좌 취소 정보가 일치하지 않습니다.");
        }
    }
}
