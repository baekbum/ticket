package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.jpa.outbox.*;
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
public class BankExpirationOutboxDispatcher {

    // 은행→PG 만료 전달 행만 조회한다.
    private final BankTransferOutboxJpaRepository events;
    // 전송 전 임대와 전송 후 수신 확인을 각각 커밋한다.
    private final BankTransferOutboxCommands commands;
    // 금융 인증을 사용하는 PG 만료 수신 API.
    private final BankExpirationNotificationClient client;
    // 최초 만료 사실과 수신 확인의 JSON 스냅샷 변환.
    private final ObjectMapper json;

    /**
     * 상태 전달 실패·응답 유실·임대 만료를 같은 만료 이벤트로 재시도한다.
     */
    @Scheduled(fixedDelayString = "${app.bank.expiration-notifications.fixed-delay-ms:30000}")
    public void retryPending() {

        for (Long id : events.findReadyIds(BankTransferOutbox.Flow.BANK_EXPIRATION,
                LocalDateTime.now(), PageRequest.of(0, 100))) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            try {
                deliverSafely(id);
            } catch (RuntimeException failure) {
                log.warn("은행 만료 전송 선점 실패: outboxId={}, errorType={}", id, failure.getClass().getSimpleName());
            }
        }
    }

    /**
     * HTTP 동안 계좌·PG·Outbox의 DB 잠금을 유지하지 않는다.
     */
    public void deliverSafely(Long id) {

        BankTransferDelivery delivery = commands.claim(id, BankTransferOutbox.Flow.BANK_EXPIRATION);

        if (delivery == null) {
            return;
        }

        try {
            BankExpirationEvent event = json.convertValue(delivery.payload(), BankExpirationEvent.class);
            BankExpirationAcknowledgement response = client.send(event);
            commands.complete(delivery, json.convertValue(response, new TypeReference<>() { }));
        } catch (RuntimeException failure) {
            try {
                commands.failed(delivery, failure.getClass().getSimpleName());
            } catch (RuntimeException storageFailure) {
                log.warn("은행 만료 통보 상태 저장 실패: outboxId={}", id);
            }
        }
    }
}
