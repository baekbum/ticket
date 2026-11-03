package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelResponse;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountCancelOutboxPublisher {

    // 가상계좌 취소 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final VirtualAccountCancelOutboxJpaRepository repository;
    // 결제 게이트웨이 처리 결과.
    private final PaymentGatewayVirtualAccountClient gateway;
    // 실패한 이벤트를 다시 처리하기 전 대기 시간(밀리초).
    @Value("${app.virtual-account.cancel-outbox.retry-delay-ms:30000}")
    private long retryDelayMs = 30000;

    /**
     * 가상계좌 취소 Outbox 이벤트 처리에서 후속 처리 대상으로 전달한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long outboxId) {

        // 원래 예매/결제 잠금 없이 Outbox만 잠근다. PG의 입금 통지가 티켓으로 돌아와도 서로 기다리지 않는다.
        VirtualAccountCancelOutbox outbox = repository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() == VirtualAccountCancelOutboxStatus.DELIVERED
                || outbox.getNextAttemptAt().isAfter(LocalDateTime.now())) return;
        try {
            validateResponse(outbox, gateway.cancel(outbox.toRequest()));
        } catch (RuntimeException e) {
            String errorType = e instanceof FeignException failure ? "HTTP_" + failure.status() : e.getClass().getSimpleName();
            outbox.failed(errorType, LocalDateTime.now().plus(Duration.ofMillis(retryDelayMs)));
            log.warn("가상계좌 취소 전달 실패: outboxId={}, errorType={}", outboxId, errorType);
            return;
        }
        outbox.delivered(LocalDateTime.now());
    }

    /**
     * 외부 응답이 후속 처리에 유효한지 검증한다.
     */
    private void validateResponse(VirtualAccountCancelOutbox outbox, GatewayVirtualAccountCancelResponse response) {

        if (response == null || response.status() != GatewayVirtualAccountStatus.CANCELLED
                || !Objects.equals(outbox.getPaymentNo(), response.paymentNo())
                || !Objects.equals(outbox.getBankName(), response.bankName())
                || !Objects.equals(outbox.getAccountNumber(), response.accountNumber())
                || response.amount() == null || outbox.toRequest().amount().compareTo(response.amount()) != 0) {
            throw new IllegalStateException("PG 계좌 취소 응답이 요청과 일치하지 않습니다.");
        }
    }
}
