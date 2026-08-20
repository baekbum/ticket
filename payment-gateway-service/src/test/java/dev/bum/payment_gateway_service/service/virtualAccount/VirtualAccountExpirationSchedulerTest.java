package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentHistoryType;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.jpa.outbox.OutboxEventStatus;
import dev.bum.payment_gateway_service.jpa.outbox.OutboxEventType;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEvent;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEventJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class VirtualAccountExpirationSchedulerTest {

    @Mock
    private DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;

    @Mock
    private DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;

    @Mock
    private VirtualAccountOutboxEventJpaRepository virtualAccountOutboxEventJpaRepository;

    @InjectMocks
    private VirtualAccountExpirationScheduler scheduler;

    @Test
    @DisplayName("입금 기한이 지난 가상계좌를 만료 처리하고 Kafka 이벤트를 발행한다")
    void expire_waiting_accounts() {
        DummyVirtualAccount virtualAccount = waitingVirtualAccount();

        given(dummyVirtualAccountJpaRepository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                eq(VirtualAccountPaymentStatus.WAITING_DEPOSIT),
                any(LocalDateTime.class)
        )).willReturn(List.of(virtualAccount), List.of());
        scheduler.expireWaitingAccounts();

        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);

        ArgumentCaptor<DummyVirtualAccountPaymentHistory> historyCaptor =
                ArgumentCaptor.forClass(DummyVirtualAccountPaymentHistory.class);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPaymentNo()).isEqualTo(virtualAccount.getPaymentNo());
        assertThat(historyCaptor.getValue().getHistoryType()).isEqualTo(VirtualAccountPaymentHistoryType.EXPIRED);

        ArgumentCaptor<VirtualAccountOutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(VirtualAccountOutboxEvent.class);
        then(virtualAccountOutboxEventJpaRepository).should().save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPaymentNo()).isEqualTo(virtualAccount.getPaymentNo());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(OutboxEventType.VIRTUAL_ACCOUNT_EXPIRED);
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("만료 대상이 남아 있으면 100건 단위로 반복 처리한다")
    void repeat_until_no_expired_accounts() {
        DummyVirtualAccount firstAccount = waitingVirtualAccount("PAY-1", "1111-1234-567890");
        DummyVirtualAccount secondAccount = waitingVirtualAccount("PAY-2", "1111-1234-567891");

        given(dummyVirtualAccountJpaRepository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                eq(VirtualAccountPaymentStatus.WAITING_DEPOSIT),
                any(LocalDateTime.class)
        )).willReturn(List.of(firstAccount), List.of(secondAccount), List.of());
        scheduler.expireWaitingAccounts();

        assertThat(firstAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
        assertThat(secondAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
        then(dummyVirtualAccountJpaRepository).should(times(3))
                .findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        eq(VirtualAccountPaymentStatus.WAITING_DEPOSIT),
                        any(LocalDateTime.class)
                );
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should(times(2))
                .save(any(DummyVirtualAccountPaymentHistory.class));
        then(virtualAccountOutboxEventJpaRepository).should(times(2))
                .save(any(VirtualAccountOutboxEvent.class));
    }

    private DummyVirtualAccount waitingVirtualAccount() {
        return waitingVirtualAccount("PAY-20260727120000-abcdef123456", "1111-1234-567890");
    }

    private DummyVirtualAccount waitingVirtualAccount(String paymentNo, String accountNumber) {
        return DummyVirtualAccount.issue(
                paymentNo,
                BankCompany.KB,
                accountNumber,
                BigDecimal.valueOf(180000),
                LocalDateTime.of(2026, 8, 20, 23, 59, 59)
        );
    }
}
