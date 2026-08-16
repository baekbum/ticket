package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationScheduler {

    private static final List<PaymentStatus> EXPIRE_TARGET_STATUSES = List.of(
            PaymentStatus.READY,
            PaymentStatus.WAITING_DEPOSIT
    );
    private static final int BATCH_SIZE = 100;

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentExpirationService paymentExpirationService;

    /**
     * 결제 만료 시각이 지난 미완료 결제를 찾아 건별 만료 처리 서비스에 위임한다.
     */
    @Scheduled(fixedDelayString = "${payment.expiration.scheduler.fixed-delay-ms:60000}")
    public void expirePayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> expiredPaymentIds = paymentJpaRepository.findExpiredPaymentIds(
                EXPIRE_TARGET_STATUSES,
                now,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (Long paymentId : expiredPaymentIds) {
            try {
                paymentExpirationService.expireIfExpired(paymentId);
            } catch (RuntimeException ex) {
                log.error("[PAYMENT][EXPIRE][FAIL] paymentId={}", paymentId, ex);
            }
        }

        if (!expiredPaymentIds.isEmpty()) {
            log.info("[PAYMENT][EXPIRE] count={}, now={}", expiredPaymentIds.size(), now);
        }
    }
}
