package dev.bum.payment_gateway_service.service.card;

import dev.bum.payment_gateway_service.jpa.card.DummyCardPaymentHistoryJpaRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class GatewayCardRecoverySchedulerTest {
    @Test
    void continuesOtherPaymentsWhenOneRecoveryFails() {
        DummyCardPaymentHistoryJpaRepository histories = mock(DummyCardPaymentHistoryJpaRepository.class);
        GatewayCardPaymentService payments = mock(GatewayCardPaymentService.class);
        when(histories.findPendingPaymentNos(any(), any())).thenReturn(List.of("PAY-1", "PAY-2"));
        when(payments.settle("PAY-1")).thenThrow(new IllegalStateException("unavailable"));
        new GatewayCardRecoveryScheduler(histories, payments).recover();
        verify(payments).settle("PAY-1");
        verify(payments).settle("PAY-2");
    }
}
