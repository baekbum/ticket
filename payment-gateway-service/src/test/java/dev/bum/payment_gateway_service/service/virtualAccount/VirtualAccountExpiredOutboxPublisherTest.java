package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.outbox.OutboxEventStatus;
import dev.bum.payment_gateway_service.jpa.outbox.OutboxEventType;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEvent;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEventJpaRepository;
import dev.bum.payment_gateway_service.kafka.virtualAccount.VirtualAccountExpiredEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualAccountExpiredOutboxPublisherTest {

    @Mock
    private VirtualAccountOutboxEventJpaRepository virtualAccountOutboxEventJpaRepository;

    @Mock
    private VirtualAccountExpiredEventProducer virtualAccountExpiredEventProducer;

    @InjectMocks
    private VirtualAccountExpiredOutboxPublisher publisher;

    @Test
    @DisplayName("대기 중인 가상계좌 만료 outbox를 Kafka로 발행하고 완료 처리한다")
    void publish_pending_virtual_account_expired_outbox() {
        VirtualAccountOutboxEvent outboxEvent = outboxEvent("PAY-1");

        given(virtualAccountOutboxEventJpaRepository.findTop100ByStatusOrderByOutboxIdAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(outboxEvent));
        given(virtualAccountExpiredEventProducer.sendExpired(any(VirtualAccountExpiredEvent.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
        assertThat(outboxEvent.getLastErrorMessage()).isNull();
        then(virtualAccountExpiredEventProducer).should().sendExpired(any(VirtualAccountExpiredEvent.class));
    }

    @Test
    @DisplayName("가상계좌 만료 outbox 발행 실패 시 대기 상태로 남기고 재시도 횟수를 증가시킨다")
    void keep_pending_when_publish_failed() {
        VirtualAccountOutboxEvent outboxEvent = outboxEvent("PAY-1");

        given(virtualAccountOutboxEventJpaRepository.findTop100ByStatusOrderByOutboxIdAsc(OutboxEventStatus.PENDING))
                .willReturn(List.of(outboxEvent));
        given(virtualAccountExpiredEventProducer.sendExpired(any(VirtualAccountExpiredEvent.class)))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka down")));

        publisher.publishPendingEvents();

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isEqualTo("kafka down");
        assertThat(outboxEvent.getPublishedAt()).isNull();
    }

    private VirtualAccountOutboxEvent outboxEvent(String paymentNo) {
        return VirtualAccountOutboxEvent.builder()
                .outboxId(1L)
                .eventType(OutboxEventType.VIRTUAL_ACCOUNT_EXPIRED)
                .status(OutboxEventStatus.PENDING)
                .paymentNo(paymentNo)
                .bankCompany(BankCompany.KB)
                .bankName("KB국민은행")
                .accountNumber("1111-1234-567890")
                .amount(BigDecimal.valueOf(180000))
                .occurredAt(LocalDateTime.of(2026, 8, 20, 23, 59, 59))
                .retryCount(0)
                .build();
    }
}
