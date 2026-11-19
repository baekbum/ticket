package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.service.paymentgateway.notification.BankCancellationNotificationResponse;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.notification.*;
import dev.bum.payment_gateway_service.service.outbox.BankTransferOutboxCommands;
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
public class GatewayBankCancellationDispatcher {

    // 은행 요청과 가맹점 결과 통보를 단계별로 독립 재시도한다.
    private final BankTransferOutboxJpaRepository events;
    // HTTP 전후 임대/재시도/단순 통보 완료의 짧은 트랜잭션.
    private final BankTransferOutboxCommands deliveries;
    // 은행 결과·PG 상태·다음 결과 Outbox를 함께 확정한다.
    private final GatewayVirtualAccountCancelCommands cancellations;
    // PG→은행 금융 전용 HTTP 클라이언트.
    private final FinancialSimulationGatewayService financial;
    // 등록된 가맹점의 URL과 암호화된 인증정보만 사용한다.
    private final MerchantNotificationTargetResolver targets;
    // 가맹점별 클래스 없이 동일한 취소 결과 DTO를 보낸다.
    private final StandardBankNotificationAdapter sender;
    // 최초 입력·통보·은행 결과 스냅샷 변환.
    private final ObjectMapper json;

    /**
     * 단계별 전달 실패만 재시도한다. 은행 업무 거절은 확정 결과로 가맹점에 전달한다.
     */
    @Scheduled(fixedDelayString = "${app.virtual-account.cancellation-outbox.fixed-delay-ms:30000}")
    public void retryPending() {

        events.findReadyIds(BankTransferOutbox.Flow.BANK_CANCEL_REQUEST, LocalDateTime.now(), PageRequest.of(0, 100))
                .forEach(id -> deliverSafely(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST));
        events.findReadyIds(BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT, LocalDateTime.now(), PageRequest.of(0, 100))
                .forEach(id -> deliverSafely(id, BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT));
    }

    /**
     * 로컬 락 없이 HTTP를 수행한다. 종료·응답 유실은 동일 키 재전달과 수신 측 멱등 이력으로 복구한다.
     */
    public void deliverSafely(Long id, BankTransferOutbox.Flow flow) {

        BankTransferDelivery delivery = deliveries.claim(id, flow);

        if (delivery == null) {
            return;
        }

        try {
            if (flow == BankTransferOutbox.Flow.BANK_CANCEL_REQUEST) {
                GatewayBankCancellationInput input = json.convertValue(delivery.payload(), GatewayBankCancellationInput.class);
                cancellations.complete(delivery, financial.requestGatewayCancellation(input.bankRequest()));
            } else if (flow == BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT) {
                GatewayBankCancellationNotification input = json.convertValue(delivery.payload(), GatewayBankCancellationNotification.class);
                MerchantNotificationTarget target = targets.resolve(input.notification().merchantId(),
                        input.notificationContract(), input.notificationTargetKey());
                BankCancellationNotificationResponse result = sender.sendCancellation(
                        target.url(), target.serviceToken(), input.notification());
                deliveries.complete(delivery, json.convertValue(result, new TypeReference<>() { }));
            } else {
                throw new IllegalArgumentException("취소 워커가 처리할 수 없는 전달 종류입니다.");
            }
        } catch (RuntimeException failure) {
            try {
                deliveries.failed(delivery, failure.getClass().getSimpleName());
            } catch (RuntimeException storageFailure) {
                log.warn("PG 취소 전달 상태 저장 실패: outboxId={}", id);
            }
        }
    }
}
