package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.*;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
@ConditionalOnProperty(name = "app.virtual-account.gateway-expiration.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayVirtualAccountExpirationScheduler {

    // 처리되지 않은 후보를 ID로 순회해 조회 실패 행 때문에 다른 결제가 밀리지 않게 한다.
    private final PaymentJpaRepository payments;
    // HTTP 전 후보 조회와 HTTP 후 최종 만료를 각각 짧은 트랜잭션으로 처리한다.
    private final GatewayVirtualAccountExpirationCommands commands;
    // 등록 가맹점 인증으로 PG 거래 상태만 조회한다. 은행을 직접 호출하지 않는다.
    private final PaymentGatewayTransactionClient gateway;
    // 후보 기한을 한국 시간으로 비교하는 애플리케이션 시계.
    private final Clock clock;

    /**
     * 로컬 기한이 지난 결제를 PG에서 확인한다. PAID·대기·조회 실패는 만료하지 않고 다음 주기에 확인한다.
     */
    @Scheduled(fixedDelayString = "${app.virtual-account.gateway-expiration.fixed-delay-ms:60000}")
    public void expirePayments() {

        LocalDateTime now = LocalDateTime.now(clock);
        long afterId = 0;

        while (!Thread.currentThread().isInterrupted()) {
            List<Long> ids = payments.findGatewayExpirationCandidateIds(now, afterId, PaymentMethod.BANK_TRANSFER,
                    List.of(PaymentStatus.READY, PaymentStatus.WAITING_DEPOSIT), PageRequest.of(0, 100));

            if (ids.isEmpty()) {
                return;
            }

            for (Long id : ids) {
                try {
                    expireIfConfirmed(id, now);
                } catch (RuntimeException failure) {
                    log.warn("PG 만료 상태 확인 보류: paymentId={}, errorType={}", id,
                            failure.getClass().getSimpleName());
                }
            }

            afterId = ids.getLast();
        }
    }

    /**
     * PG 조회는 DB 트랜잭션 밖에서 수행하고 미등록 거래나 응답 유실은 만료 근거로 삼지 않는다.
     */
    private void expireIfConfirmed(Long paymentId, LocalDateTime now) {

        GatewayVirtualAccountExpirationCandidate candidate = commands.findCandidate(paymentId, now);

        if (candidate == null) {
            return;
        }

        GatewayPaymentPrepareResponse response = candidate.pgPaymentId() == null
                ? gateway.findByPaymentNo(candidate.paymentNo())
                : gateway.findByPgPaymentId(candidate.pgPaymentId());
        commands.expireIfConfirmed(candidate, response);
    }
}
