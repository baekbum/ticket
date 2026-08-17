package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.kafka.virtualAccount.VirtualAccountDepositEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualAccountDepositEventPublishSchedulerTest {

    @Mock
    private DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;

    @Mock
    private DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;

    @Mock
    private VirtualAccountDepositEventProducer virtualAccountDepositEventProducer;

    @InjectMocks
    private VirtualAccountDepositEventPublishScheduler scheduler;

    @Test
    @DisplayName("입금 완료 계좌를 Kafka 이벤트로 발행하고 중복 방지 상태로 변경한다")
    void publish_deposit_completed_events() {
        DummyVirtualAccount virtualAccount = depositedVirtualAccount();

        given(dummyVirtualAccountJpaRepository.findTop100ByStatusOrderByDepositedAtAsc(VirtualAccountPaymentStatus.DEPOSITED))
                .willReturn(List.of(virtualAccount));
        given(virtualAccountDepositEventProducer.sendDepositCompleted(virtualAccount))
                .willReturn(CompletableFuture.completedFuture(null));

        scheduler.publishDepositCompletedEvents();

        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSIT_EVENT_PUBLISHED);

        ArgumentCaptor<DummyVirtualAccountPaymentHistory> historyCaptor =
                ArgumentCaptor.forClass(DummyVirtualAccountPaymentHistory.class);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPaymentNo()).isEqualTo(virtualAccount.getPaymentNo());
    }

    private DummyVirtualAccount depositedVirtualAccount() {
        DummyVirtualAccount virtualAccount = DummyVirtualAccount.issue(
                "PAY-20260727120000-abcdef123456",
                BankCompany.KB,
                "1111-1234-567890",
                BigDecimal.valueOf(180000),
                LocalDateTime.of(2099, 7, 27, 23, 59, 59)
        );
        virtualAccount.deposit("아이유", LocalDateTime.of(2026, 8, 17, 12, 0));
        return virtualAccount;
    }
}
