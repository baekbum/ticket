package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelResponse;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.payment.virtualAccount.VirtualAccountCancelOutbox;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
public class VirtualAccountCancelOutboxPublisher {

    // HTTP 전후의 상태 저장을 짧은 트랜잭션으로 분리한다.
    private final VirtualAccountCancelOutboxCommands commands;
    // PG 식별자가 없는 기존 계좌만 기존 계약으로 취소한다.
    private final PaymentGatewayVirtualAccountClient gateway;
    // 신규 PG 원거래를 인증된 가맹점 범위로 취소한다.
    private final PaymentGatewayTransactionClient transactions;
    // 발급 결과를 받기 전에 취소된 원거래만 복구하여 은행 취소를 이어간다.
    private final VirtualAccountIssuanceCheckoutService issuance;
    // 서버에 등록된 Ticksy 가맹점 코드.
    @Value("${app.payment-gateway.merchant-id:1}")
    private long merchantId = 1;
    // 실패한 이벤트를 다시 처리하기 전 대기 시간(밀리초).
    @Value("${app.virtual-account.cancel-outbox.retry-delay-ms:30000}")
    private long retryDelayMs = 30000;

    /**
     * 취소 요청은 잠금 밖에서 전달한다. 동일 원거래 취소 키로 응답 유실을 복구한다.
     */
    public void deliver(Long outboxId) {

        long attempt = 0;

        try {
            VirtualAccountCancelOutbox outbox = commands.claimDelivery(outboxId);

            if (outbox == null) {
                return;
            }

            attempt = outbox.getDeliveryAttempt();

            if (outbox.getBankCompany() == null) {
                validateLegacyResponse(outbox, gateway.cancel(outbox.toRequest()));
                commands.delivered(outboxId, attempt, null, null);
                return;
            }

            if (outbox.getPgPaymentId() == null || outbox.getAccountNumber() == null) {
                issuance.prepare(outbox.getPaymentNo());
                issuance.issue(outbox.getPaymentNo());
                outbox = commands.findClaimedDelivery(outboxId, attempt);

                if (outbox == null) {
                    return;
                }
            }

            ResponseEntity<GatewayAccountCancelResponse> received = transactions.cancel(outbox.getPgPaymentId(),
                    outbox.toGatewayRequest(merchantId));
            GatewayAccountCancelResponse response = validateGatewayResponse(outbox, received);
            commands.delivered(outboxId, attempt, response.status(), response.reason());
        } catch (RuntimeException failure) {
            String errorType = failure instanceof FeignException http ? "HTTP_" + http.status()
                    : failure.getClass().getSimpleName();
            commands.failed(outboxId, attempt, errorType, LocalDateTime.now().plus(Duration.ofMillis(retryDelayMs)));
            log.warn("가상계좌 취소 전달 실패: outboxId={}, errorType={}", outboxId, errorType);
        }
    }

    /**
     * 기존 거래의 동기 취소 응답은 계좌·금액까지 비교한다.
     */
    private void validateLegacyResponse(VirtualAccountCancelOutbox outbox, GatewayVirtualAccountCancelResponse response) {

        if (response == null || response.status() != GatewayVirtualAccountStatus.CANCELLED
                || !Objects.equals(outbox.getPaymentNo(), response.paymentNo())
                || !Objects.equals(outbox.getBankName(), response.bankName())
                || !Objects.equals(outbox.getAccountNumber(), response.accountNumber())
                || response.amount() == null || outbox.toRequest().amount().compareTo(response.amount()) != 0) {
            throw new IllegalStateException("PG 계좌 취소 응답이 요청과 일치하지 않습니다.");
        }
    }

    /**
     * 접수와 최종 결과를 분리하고 다른 거래·이벤트 또는 잘못된 HTTP 상태의 성공 응답을 거절한다.
     */
    private GatewayAccountCancelResponse validateGatewayResponse(
            VirtualAccountCancelOutbox outbox,
            ResponseEntity<GatewayAccountCancelResponse> received
    ) {

        GatewayAccountCancelResponse response = received == null ? null : received.getBody();

        if (response == null || !Objects.equals(outbox.getPgPaymentId(), response.pgPaymentId())
                || !outbox.getPaymentNo().equals(response.paymentNo())
                || !(outbox.getPgPaymentId() + ":CANCEL:INITIAL").equals(response.eventId())
                || response.fundsReturned() || response.pgStatus() == null) {
            throw new IllegalStateException("PG 취소 접수 결과가 최초 원거래와 일치하지 않습니다.");
        }

        boolean pending = received.getStatusCode().value() == 202 && "PROCESSING".equals(response.status())
                && response.cancelledAt() == null && response.reason() == null;
        boolean completed = received.getStatusCode().value() == 200
                && (("CANCELLED".equals(response.status()) && response.cancelledAt() != null
                        && "CANCELLED".equals(response.pgStatus()))
                    || ("REJECTED".equals(response.status()) && response.cancelledAt() == null))
                && response.reason() != null && !response.reason().isBlank();

        if (!pending && !completed) {
            throw new IllegalStateException("PG 취소 접수와 금융 결과의 계약이 일치하지 않습니다.");
        }

        return response;
    }
}
