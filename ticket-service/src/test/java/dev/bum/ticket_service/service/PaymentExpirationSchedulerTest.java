package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.service.payment.PaymentExpirationScheduler;
import dev.bum.ticket_service.service.payment.PaymentExpirationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationSchedulerTest {

    @Mock
    private PaymentJpaRepository paymentJpaRepository;

    @Mock
    private PaymentExpirationService paymentExpirationService;

    @InjectMocks
    private PaymentExpirationScheduler scheduler;

    @Test
    @DisplayName("만료된 미완료 결제마다 만료 처리 서비스를 호출한다")
    void expire_payments() {
        given(paymentJpaRepository.findExpiredPaymentIds(
                argThat(statuses -> containsExpireTargetStatuses(statuses)),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).willReturn(List.of(1L, 2L));

        scheduler.expirePayments();

        then(paymentExpirationService).should().expireIfExpired(1L);
        then(paymentExpirationService).should().expireIfExpired(2L);
    }

    private boolean containsExpireTargetStatuses(Collection<PaymentStatus> statuses) {
        return statuses.contains(PaymentStatus.READY)
                && statuses.contains(PaymentStatus.WAITING_DEPOSIT)
                && statuses.size() == 2;
    }
}
