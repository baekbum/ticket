package dev.bum.payment_gateway_service.service.card;

import dev.bum.payment_gateway_service.jpa.card.CardPaymentHistoryStatus;
import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.card.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayCardRecoveryScheduler {
    private final DummyCardPaymentHistoryJpaRepository histories;
    private final GatewayCardPaymentService payments;

    @Scheduled(fixedDelayString = "${app.card.recovery.fixed-delay-ms:30000}")
    public void recover() {
        List<String> paymentNos = histories.findPendingPaymentNos(
                List.of(CardPaymentHistoryStatus.APPROVED, CardPaymentHistoryStatus.TICKET_PAYMENT_FAILED),
                PageRequest.of(0, 100));
        for (String paymentNo : paymentNos) {
            try {
                payments.settle(paymentNo);
            } catch (RuntimeException failure) {
                log.warn("카드 결제 재처리 대기: paymentNo={}", paymentNo);
            }
        }
    }
}
