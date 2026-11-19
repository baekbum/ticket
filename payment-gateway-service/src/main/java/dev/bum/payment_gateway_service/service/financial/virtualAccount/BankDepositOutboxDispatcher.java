package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
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
public class BankDepositOutboxDispatcher {

    // 은행→PG 전달 행만 조회한다. 가맹점 통보 상태에는 관여하지 않는다.
    private final BankTransferOutboxJpaRepository events;
    // 기존 은행 자료 중 통보 의도가 누락된 행도 편입한다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 기존 최초 입금을 변경하지 않고 은행 통보만 준비한다.
    private final BankVirtualAccountDepositService deposits;
    // HTTP 전에 임대를 커밋하고 응답 후 전달 상태만 갱신한다.
    private final BankTransferOutboxCommands commands;
    // PG의 인증된 입금 수신 API를 호출한다.
    private final BankDepositNotificationClient client;
    // 최초 저장한 금융 스냅샷과 확인 응답의 JSON 변환.
    private final ObjectMapper json;

    /**
     * 정상 확인을 받지 못한 은행 입금 이벤트를 같은 키와 내용으로 전달한다.
     */
    @Scheduled(fixedDelayString = "${app.bank.deposit-notifications.fixed-delay-ms:30000}")
    public void retryPending() {

        accounts.findUnnotifiedDepositIds(PageRequest.of(0, 100)).forEach(deposits::enqueueExistingDeposit);
        events.findReadyIds(BankTransferOutbox.Flow.BANK_DEPOSIT, LocalDateTime.now(), PageRequest.of(0, 100))
                .forEach(this::deliverSafely);
    }

    /**
     * PG가 커밋했지만 응답이 유실돼도 다음 시도에서 같은 확인을 받을 수 있다.
     */
    public void deliverSafely(Long id) {

        BankTransferDelivery delivery = commands.claim(id, BankTransferOutbox.Flow.BANK_DEPOSIT);

        if (delivery == null) {
            return;
        }

        try {
            BankDepositEvent event = json.convertValue(delivery.payload(), BankDepositEvent.class);
            event.validate();
            BankDepositAcknowledgement response = client.send(event);
            commands.complete(delivery, json.convertValue(response, new TypeReference<>() { }));
        } catch (RuntimeException failure) {
            try {
                commands.failed(delivery, failure.getClass().getSimpleName());
            } catch (RuntimeException storageFailure) {
                log.warn("은행 입금 통보 상태 저장 실패: outboxId={}", id);
            }
        }
    }
}
