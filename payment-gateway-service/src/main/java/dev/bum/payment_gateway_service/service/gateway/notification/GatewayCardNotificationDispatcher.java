package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse;
import dev.bum.payment_gateway_service.dto.gateway.CardNotificationSnapshot;
import dev.bum.payment_gateway_service.dto.gateway.MerchantNotificationTarget;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayCardNotificationJpaRepository;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardNotificationDispatcher {

    // 재시도할 통보 ID만 조회한다.
    private final GatewayCardNotificationJpaRepository notifications;
    // 짧은 임대/완료/실패 트랜잭션.
    private final GatewayCardNotificationCommands commands;
    // 가맹점 등록 설정에서 전체 수신 URL과 인증값을 읽는다.
    private final MerchantNotificationTargetResolver targets;
    // 모든 가맹점이 공유하는 공통 규격 전송기.
    private final MerchantCardNotificationAdapter sender;
    // 확정 거절에 대해서만 실제 카드 승인 보상을 HTTP로 요청한다.
    private final FinancialSimulationGatewayService financial;

    /**
     * 미전달 이벤트와 프로세스 중단 후 만료된 임대를 같은 이벤트 ID로 재전달한다.
     */
    @Scheduled(fixedDelayString = "${app.merchants.notifications.fixed-delay-ms:30000}")
    public void retryPending() {

        notifications.findReadyIds(LocalDateTime.now(), PageRequest.of(0, 100)).forEach(this::deliverSafely);
    }

    /**
     * HTTP 실패를 금융 실패로 바꾸지 않고 동일 통보를 재시도한다.
     */
    public void deliverSafely(Long id) {

        CardNotificationSnapshot snapshot = commands.claim(id);

        if (snapshot == null) {
            return;
        }

        try {
            MerchantNotificationTarget target = targets.resolve(
                    snapshot.merchantId(), snapshot.notificationContract(), snapshot.targetKey());
            boolean accepted = sender.send(target.url(), target.serviceToken(), snapshot.toNotification());
            FinancialCardApprovalResponse cancelled = accepted ? null : financial.cancelGatewayCard(snapshot.pgPaymentId());
            commands.complete(snapshot, cancelled);
        } catch (RuntimeException failure) {
            try {
                commands.failed(snapshot, failure.getClass().getSimpleName());
            } catch (RuntimeException storageFailure) {
                log.warn("가맹점 카드 통보 상태 저장 실패: notificationId={}", id);
            }
        }
    }
}
