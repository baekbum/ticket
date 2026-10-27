package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountDepositOutbox;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountDepositOutboxJpaRepository;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountDepositOutboxStatus;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountDepositOutboxPublisher {
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final VirtualAccountDepositOutboxJpaRepository repository;
    private final TicketPaymentClient ticket;

    @Value("${app.virtual-account.deposit-outbox.retry-delay-ms:30000}")
    private long retryDelayMs = 30000;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long outboxId) {
        // 계좌 대신 Outbox 행만 잠가 여러 서버의 동일 알림 전달을 직렬화한다.
        VirtualAccountDepositOutbox outbox = repository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() == VirtualAccountDepositOutboxStatus.DELIVERED
                || outbox.getNextAttemptAt().isAfter(LocalDateTime.now())) return;

        try {
            PaymentResponse response = ticket.completeVirtualAccountDeposit(outbox.toRequest());
            validateResponse(outbox, response);
        } catch (RuntimeException e) {
            // 응답 본문·계좌·입금자명을 실패 이력이나 로그에 남기지 않는다.
            String errorType = e instanceof FeignException failure
                    ? "HTTP_" + failure.status() : e.getClass().getSimpleName();
            outbox.failed(errorType, LocalDateTime.now().plus(Duration.ofMillis(retryDelayMs)));
            log.warn("가상계좌 입금 알림 전달 실패: outboxId={}, errorType={}", outboxId, errorType);
            return;
        }
        // 티켓이 커밋한 성공 응답을 확인한 경우에만 전달 완료로 확정한다.
        outbox.delivered(LocalDateTime.now());
    }

    private void validateResponse(VirtualAccountDepositOutbox outbox, PaymentResponse response) {
        boolean completed = response != null && (response.getStatus() == PaymentStatus.PAID
                || response.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || response.getStatus() == PaymentStatus.REFUNDED);
        if (!completed || response.getMethod() != PaymentMethod.BANK_TRANSFER
                || !outbox.getPaymentNo().equals(response.getPaymentNo())
                || response.getAmount() == null
                || outbox.getAmount().compareTo(BigDecimal.valueOf(response.getAmount())) != 0
                || !outbox.getAccountNumber().equals(response.getAccountNumber())
                || !outbox.getBankName().equals(response.getBankName())
                || !outbox.getDepositorName().equals(response.getDepositorName())
                || !outbox.getDepositedAt().format(RESPONSE_TIME_FORMAT).equals(response.getPaidAt())) {
            throw new IllegalStateException("티켓 입금 완료 응답이 저장된 입금 정보와 일치하지 않습니다.");
        }
    }
}
