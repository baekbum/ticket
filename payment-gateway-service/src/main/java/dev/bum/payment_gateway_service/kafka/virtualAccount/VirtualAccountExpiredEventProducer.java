package dev.bum.payment_gateway_service.kafka.virtualAccount;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualAccountExpiredEventProducer {

    private final KafkaTemplate<String, VirtualAccountExpiredEvent> kafkaTemplate;

    @Value("${topic.payment.virtual-account.expired.name:virtual-account-expired}")
    private String virtualAccountExpiredTopic;

    public CompletableFuture<?> sendExpired(DummyVirtualAccount virtualAccount, LocalDateTime expiredAt) {
        VirtualAccountExpiredEvent event = VirtualAccountExpiredEvent.builder()
                .paymentNo(virtualAccount.getPaymentNo())
                .bankCompany(virtualAccount.getBankCompany())
                .bankName(virtualAccount.getBankName())
                .accountNumber(virtualAccount.getAccountNumber())
                .amount(virtualAccount.getAmount())
                .expiredAt(expiredAt)
                .build();

        return kafkaTemplate.send(virtualAccountExpiredTopic, virtualAccount.getPaymentNo(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.info("가상계좌 만료 이벤트 발행 성공: paymentNo={}", virtualAccount.getPaymentNo());
                    } else {
                        log.error("가상계좌 만료 이벤트 발행 실패: paymentNo={}", virtualAccount.getPaymentNo(), throwable);
                    }
                });
    }
}
