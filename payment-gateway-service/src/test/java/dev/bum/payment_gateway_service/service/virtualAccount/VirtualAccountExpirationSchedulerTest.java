package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentHistoryType;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.kafka.virtualAccount.VirtualAccountExpiredEventProducer;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualAccountExpirationSchedulerTest {

    @Mock
    private DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;

    @Mock
    private DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;

    @Mock
    private VirtualAccountExpiredEventProducer virtualAccountExpiredEventProducer;

    @InjectMocks
    private VirtualAccountExpirationScheduler scheduler;

    @Test
    @DisplayName("입금 기한이 지난 가상계좌를 만료 처리하고 Kafka 이벤트를 발행한다")
    void expire_waiting_accounts() {
        DummyVirtualAccount virtualAccount = waitingVirtualAccount();

        given(dummyVirtualAccountJpaRepository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                eq(VirtualAccountPaymentStatus.WAITING_DEPOSIT),
                any(LocalDateTime.class)
        )).willReturn(List.of(virtualAccount));
        given(virtualAccountExpiredEventProducer.sendExpired(eq(virtualAccount), any(LocalDateTime.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        scheduler.expireWaitingAccounts();

        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);

        ArgumentCaptor<DummyVirtualAccountPaymentHistory> historyCaptor =
                ArgumentCaptor.forClass(DummyVirtualAccountPaymentHistory.class);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPaymentNo()).isEqualTo(virtualAccount.getPaymentNo());
        assertThat(historyCaptor.getValue().getHistoryType()).isEqualTo(VirtualAccountPaymentHistoryType.EXPIRED);
    }

    private DummyVirtualAccount waitingVirtualAccount() {
        return DummyVirtualAccount.issue(
                "PAY-20260727120000-abcdef123456",
                BankCompany.KB,
                "1111-1234-567890",
                BigDecimal.valueOf(180000),
                LocalDateTime.of(2026, 8, 20, 23, 59, 59)
        );
    }
}
