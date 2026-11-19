package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.common.service.paymentgateway.notification.BankDepositNotificationResponse;
import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayBankNotificationJpaRepository;
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
public class GatewayBankNotificationDispatcher {

    // 기존 Ticksy 전용 Outbox와 분리한 가맹점 범위 PG→가맹점 입금 통보.
    private final GatewayBankNotificationJpaRepository notifications;
    // 임대·완료·실패의 짧은 로컬 트랜잭션.
    private final GatewayBankNotificationCommands commands;
    // 가맹점 ID와 등록 대상 키에서만 URL·발신 인증값을 조회한다.
    private final MerchantNotificationTargetResolver targets;
    // 가맹점별 클래스 없이 공통 규격으로 보내는 전송기.
    private final StandardBankNotificationAdapter sender;
    // 취소된 예매의 계좌 정리를 요청한다. 실제 자금 반환 API가 아니다.
    private final FinancialSimulationGatewayService financial;

    /**
     * 미전달 또는 프로세스 종료 후 임대가 만료된 통보를 같은 이벤트로 재전달한다.
     */
    @Scheduled(fixedDelayString = "${app.merchants.notifications.fixed-delay-ms:30000}")
    public void retryPending() {

        notifications.findReadyIds(LocalDateTime.now(), PageRequest.of(0, 100)).forEach(this::deliverSafely);
    }

    /**
     * 금융/PG/Outbox 락 없이 HTTP를 수행하고 현재 임대의 결과만 확정한다.
     */
    public void deliverSafely(Long id) {

        BankNotificationSnapshot snapshot = commands.claim(id);

        if (snapshot == null) {
            return;
        }

        try {
            MerchantNotificationTarget target = targets.resolve(
                    snapshot.merchantId(), snapshot.notificationContract(), snapshot.targetKey());
            BankDepositNotificationResponse.Outcome outcome = sender.send(
                    target.url(), target.serviceToken(), snapshot.toNotification());
            BankVirtualAccountDepositResponse cancelled =
                    outcome == BankDepositNotificationResponse.Outcome.RESERVATION_CANCELLED
                            ? financial.rejectGatewayDeposit(snapshot.pgPaymentId()) : null;
            commands.complete(snapshot, cancelled);
        } catch (RuntimeException failure) {
            try {
                commands.failed(snapshot, failure.getClass().getSimpleName());
            } catch (RuntimeException storageFailure) {
                log.warn("가맹점 입금 통보 상태 저장 실패: notificationId={}", id);
            }
        }
    }
}
