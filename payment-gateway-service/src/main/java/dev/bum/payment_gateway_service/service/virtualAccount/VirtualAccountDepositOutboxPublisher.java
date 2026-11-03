package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteResponse;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
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

    // 외부 응답에 사용할 날짜와 시각 표시 형식.
    private static final DateTimeFormatter RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // 가상계좌 입금 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final VirtualAccountDepositOutboxJpaRepository repository;
    // 티켓 결제 연동 API를 호출하는 클라이언트.
    private final TicketPaymentClient ticket;
    // 가상계좌 취소 업무 처리를 담당하는 서비스.
    private final VirtualAccountCancellationService cancellationService;
    // 실패한 이벤트를 다시 처리하기 전 대기 시간(밀리초).
    @Value("${app.virtual-account.deposit-outbox.retry-delay-ms:30000}")
    private long retryDelayMs = 30000;

    /**
     * 가상계좌 입금 Outbox 이벤트 처리에서 후속 처리 대상으로 전달한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long outboxId) {

        // 계좌 대신 Outbox 행만 잠가 여러 서버의 동일 알림 전달을 직렬화한다.
        VirtualAccountDepositOutbox outbox = repository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() == VirtualAccountDepositOutboxStatus.DELIVERED
                || outbox.getNextAttemptAt().isAfter(LocalDateTime.now())) return;

        VirtualAccountDepositCompleteResponse response;
        try {
            response = ticket.completeVirtualAccountDeposit(outbox.toRequest());
            validateResponse(outbox, response);
        } catch (RuntimeException e) {
            // 응답 본문·계좌·입금자명을 실패 이력이나 로그에 남기지 않는다.
            String errorType = e instanceof FeignException failure
                    ? "HTTP_" + failure.status() : e.getClass().getSimpleName();
            outbox.failed(errorType, LocalDateTime.now().plus(Duration.ofMillis(retryDelayMs)));
            log.warn("가상계좌 입금 알림 전달 실패: outboxId={}, errorType={}", outboxId, errorType);
            return;
        }
        if (response.result() == VirtualAccountDepositCompleteResponse.Result.RESERVATION_CANCELLED) {
            // 계좌 취소와 입금 통지 완료는 같은 PG 트랜잭션이다. 실패하면 둘 다 롤백하여 재전달한다.
            cancellationService.cancel(new GatewayVirtualAccountCancelRequest(
                    outbox.getPaymentNo(), outbox.getBankName(), outbox.getAccountNumber(), outbox.getAmount()));
        }
        // 티켓이 커밋한 성공 응답을 확인한 경우에만 전달 완료로 확정한다.
        outbox.delivered(LocalDateTime.now());
    }

    /**
     * 외부 응답이 후속 처리에 유효한지 검증한다.
     */
    private void validateResponse(VirtualAccountDepositOutbox outbox, VirtualAccountDepositCompleteResponse result) {

        PaymentResponse response = result == null ? null : result.payment();
        boolean cancelled = result != null
                && result.result() == VirtualAccountDepositCompleteResponse.Result.RESERVATION_CANCELLED
                && response != null && response.getStatus() == PaymentStatus.CANCELLED;
        boolean completed = result != null && result.result() == VirtualAccountDepositCompleteResponse.Result.APPLIED
                && response != null && (response.getStatus() == PaymentStatus.PAID
                || response.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || response.getStatus() == PaymentStatus.REFUNDED);
        if ((!completed && !cancelled) || response.getMethod() != PaymentMethod.BANK_TRANSFER
                || !outbox.getPaymentNo().equals(response.getPaymentNo())
                || response.getAmount() == null
                || outbox.getAmount().compareTo(BigDecimal.valueOf(response.getAmount())) != 0
                || !outbox.getAccountNumber().equals(response.getAccountNumber())
                || !outbox.getBankName().equals(response.getBankName())
                || (completed && (!outbox.getDepositorName().equals(response.getDepositorName())
                || !outbox.getDepositedAt().format(RESPONSE_TIME_FORMAT).equals(response.getPaidAt())))) {
            throw new IllegalStateException("티켓 입금 완료 응답이 저장된 입금 정보와 일치하지 않습니다.");
        }
    }
}
