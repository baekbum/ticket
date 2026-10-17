package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentRefundRollbackRecorderTest {
    @InjectMocks PaymentRefundRollbackRecorder recorder;
    @Mock ReservationJpaRepository reservations;
    @Mock PaymentRefundProcessJpaRepository processes;
    @Mock PaymentRefundProcess process;

    private final LocalDateTime originalTime = LocalDateTime.of(2026, 10, 17, 12, 0);

    @BeforeEach
    void setUp() {
        given(reservations.findByIdForUpdate(1L)).willReturn(Optional.of(Reservation.builder().build()));
        given(processes.findByIdForUpdate(10L)).willReturn(Optional.of(process));
    }

    @Test
    void lateFailureCannotOverwriteCompletedProcess() {
        given(process.getStatus()).willReturn(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        recorder.record(record(PaymentRefundProcessStatus.GATEWAY_FAILED));
        then(process).should(never()).gatewayFailed("실패");
        then(process).should(never()).localFailed("실패");
    }

    @Test
    void lateFailureCannotOverwriteNewerAttempt() {
        given(process.getStatus()).willReturn(PaymentRefundProcessStatus.GATEWAY_FAILED);
        given(process.getUpdatedAt()).willReturn(originalTime.plusSeconds(1));
        recorder.record(record(PaymentRefundProcessStatus.GATEWAY_FAILED));
        then(process).should(never()).gatewayFailed("실패");
    }

    @Test
    void gatewayFailureCannotUndoConfirmedSuccess() {
        given(process.getStatus()).willReturn(PaymentRefundProcessStatus.LOCAL_FAILED);
        given(process.getUpdatedAt()).willReturn(originalTime);
        recorder.record(record(PaymentRefundProcessStatus.GATEWAY_FAILED));
        then(process).should(never()).gatewayFailed("실패");
    }

    @Test
    void confirmedSuccessIsPreservedAsLocalRecoveryTarget() {
        given(process.getStatus()).willReturn(PaymentRefundProcessStatus.GATEWAY_FAILED);
        recorder.record(record(PaymentRefundProcessStatus.LOCAL_FAILED));
        then(process).should().gatewaySucceeded();
        then(process).should().localFailed("실패");
    }

    private PaymentRefundRollbackRecord record(PaymentRefundProcessStatus observed) {
        return PaymentRefundRollbackRecord.builder()
                .processId(10L).reservationId(1L).expectedUpdatedAt(originalTime)
                .observedStatus(observed).failureReason("실패").build();
    }
}
