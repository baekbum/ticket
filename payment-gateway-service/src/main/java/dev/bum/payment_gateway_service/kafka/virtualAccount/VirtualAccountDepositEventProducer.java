package dev.bum.payment_gateway_service.kafka.virtualAccount;

import dev.bum.common.kafka.payment.VirtualAccountDepositCompletedEvent;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualAccountDepositEventProducer {

    private final KafkaTemplate<String, VirtualAccountDepositCompletedEvent> kafkaTemplate;

    @Value("${topic.payment.virtual-account.deposited.name:virtual-account-deposited}")
    private String virtualAccountDepositedTopic;

    public CompletableFuture<?> sendDepositCompleted(DummyVirtualAccount virtualAccount) {
        VirtualAccountDepositCompletedEvent event = VirtualAccountDepositCompletedEvent.builder()
                .paymentNo(virtualAccount.getPaymentNo())
                .bankCompany(virtualAccount.getBankCompany())
                .bankName(virtualAccount.getBankName())
                .accountNumber(virtualAccount.getAccountNumber())
                .depositorName(virtualAccount.getDepositorName())
                .amount(virtualAccount.getAmount())
                .depositedAt(virtualAccount.getDepositedAt())
                .build();

        return kafkaTemplate.send(virtualAccountDepositedTopic, virtualAccount.getPaymentNo(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.info("가상계좌 입금 완료 이벤트 발행 성공: paymentNo={}", virtualAccount.getPaymentNo());
                    } else {
                        log.error("가상계좌 입금 완료 이벤트 발행 실패: paymentNo={}", virtualAccount.getPaymentNo(), throwable);
                    }
                });
    }
}
