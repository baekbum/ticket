package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse.Outcome;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.feign.paymentgateway.GatewayCardPaymentRefundRequest;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayRefundClient;
import dev.bum.ticket_service.exception.reservation.ReservationCancellationInProgressException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.payment.card.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepositoryImpl;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepositoryImpl;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.payment.refund.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessCreator;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundRollbackRecorder;
import dev.bum.ticket_service.service.payment.refund.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.Builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReservationRepositoryImpl.class, ReservationService.class, ReservationManagementService.class, ReservationCancellationService.class,
        PaymentRefundProcessService.class, PaymentRefundProcessCreator.class, PaymentRefundRollbackRecorder.class, CardPaymentRefundService.class,
        UserCouponRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservationCancellationConcurrencyTest {
    @MockitoBean Clock clock;
    @MockitoBean PaymentGatewayRefundClient refundResolution;

    @BeforeEach
    void configureTimeAndGatewayResolution() {
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T03:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        given(refundResolution.resolve(any())).willAnswer(invocation -> {
            GatewayRefundResolutionRequest request = invocation.getArgument(0);
            return GatewayRefundResolutionResponse.builder()
                    .paymentRefundProcessId(request.paymentRefundProcessId())
                    .paymentNo(request.paymentNo())
                    .outcome(Outcome.NOT_FOUND)
                    .build();
        });
    }

    @Autowired ReservationService userCancellation;
    @Autowired ReservationManagementService adminCancellation;
    @Autowired PaymentRefundProcessService refundService;
    @MockitoSpyBean PaymentRefundRollbackRecorder rollbackRecorder;
    @Autowired ReservationJpaRepository reservations;
    @Autowired PaymentJpaRepository payments;
    @Autowired PaymentRefundProcessJpaRepository processes;
    @Autowired PaymentRefundHistoryJpaRepository refundHistories;
    @Autowired EventJpaRepository events;
    @Autowired SeatJpaRepository seats;
    @Autowired TicketJpaRepository tickets;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean JPAQueryFactory queryFactory;
    @MockitoBean EventRepository eventRepository;
    @MockitoBean SeatRepository seatRepository;
    @MockitoBean TicketRepository ticketRepository;
    @MockitoBean TicketPurchaseCountService purchaseCounts;
    @MockitoBean SeatCacheService seatCache;
    @MockitoBean PaymentGatewayCardClient gateway;
    @MockitoBean VirtualAccountPaymentRefundService virtualAccountRefunds;

    @Test
    void identicalRetryAfterDeadlineAndMidnightUsesOriginalSnapshot() {
        Fixture fixture = fixture(false);
        LocalDateTime capturedAt = LocalDateTime.of(2026, 10, 14, 23, 49);
        Long processId = new TransactionTemplate(transactionManager).execute(status -> {
            Payment payment = payments.findById(fixture.paymentId()).orElseThrow();
            payment.getReservation().getEvent().update(UpdateEventRequest.builder()
                    .cancelDeadlineAt(LocalDateTime.of(2026, 10, 14, 23, 59, 59)).build());
            PaymentRefundProcess process = PaymentRefundProcess.create(
                    payment, tickets.findAllByTicketIdIn(fixture.ticketIds().subList(0, 2)),
                    18000, 2000, false, null, capturedAt, false
            );
            process.gatewayFailed("PG 응답 유실");
            return processes.saveAndFlush(process).getPaymentRefundProcessId();
        });
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T14:55:00Z"));

        userCancellation.cancelMyReservation("owner", fixture.reservationId(), request(fixture.ticketIds().subList(0, 2)));

        ArgumentCaptor<GatewayCardPaymentRefundRequest> requested = ArgumentCaptor.forClass(GatewayCardPaymentRefundRequest.class);
        verify(gateway).refund(requested.capture());
        assertThat(requested.getValue().getPaymentRefundProcessId()).isEqualTo(processId);
        assertThat(requested.getValue().getRefundAmount()).isEqualByComparingTo("18000");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Payment payment = payments.findById(fixture.paymentId()).orElseThrow();
            assertThat(payment.getRefundedAmount()).isEqualTo(18000);
            assertThat(payment.getCancellationFeeAmount()).isEqualTo(2000);
            assertThat(processes.findById(processId).orElseThrow().getRefund().getSnapshot().getCapturedAt()).isEqualTo(capturedAt);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void changedRequestRecoversOnlyOriginalTicketsWhenPgConfirmsSuccess(boolean admin) {
        Fixture fixture = fixture(false);
        Long processId = new TransactionTemplate(transactionManager).execute(status -> {
            Payment payment = payments.findById(fixture.paymentId()).orElseThrow();
            PaymentRefundProcess process = PaymentRefundProcess.create(
                    payment, tickets.findAllByTicketIdIn(fixture.ticketIds().subList(0, 2)), 20000, 0, false, null
            );
            process.gatewayFailed("PG 응답 유실");
            return processes.saveAndFlush(process).getPaymentRefundProcessId();
        });
        doAnswer(invocation -> GatewayRefundResolutionResponse.builder()
                .paymentRefundProcessId(processId)
                .paymentNo("PAY-LOCK")
                .outcome(Outcome.SUCCEEDED)
                .build())
                .when(refundResolution).resolve(any());

        CancelReservationResponse response = admin
                ? adminCancellation.cancel(fixture.reservationId(), request(fixture.ticketIds()))
                : userCancellation.cancelMyReservation("owner", fixture.reservationId(), request(fixture.ticketIds()));

        assertThat(response.previousRequestRecovered()).isTrue();
        assertThat(response.paymentRefundProcessId()).isEqualTo(processId);
        assertThat(response.cancelledTicketIds()).containsExactlyElementsOf(fixture.ticketIds().subList(0, 2));
        assertThat(response.message()).contains("이전 요청", "변경 요청은 추가로 처리하지 않았습니다");
        assertThat(processes.findById(processId).orElseThrow().getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        assertThat(processes.count()).isEqualTo(1);
        assertResult(fixture, 20000, 2);
        verify(gateway, times(0)).refund(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentRequestIsRejectedAndExplicitRetryReusesProcessAfterRollback(boolean localFailure) throws Exception {
        Fixture fixture = fixture(false);
        CountDownLatch gatewayEntered = new CountDownLatch(1);
        CountDownLatch failGateway = new CountDownLatch(1);
        Answer<Object> failFirstAttempt = invocation -> {
            if (gatewayEntered.getCount() == 1) {
                gatewayEntered.countDown();
                if (!failGateway.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("PG 대기 시간 초과");
                throw new IllegalStateException(localFailure ? "로컬 반영 실패" : "PG 응답 유실");
            }
            return null;
        };
        if (localFailure) {
            doAnswer(invocation -> {
                GatewayRefundResolutionRequest request = invocation.getArgument(0);
                return GatewayRefundResolutionResponse.builder()
                        .paymentRefundProcessId(request.paymentRefundProcessId()).paymentNo(request.paymentNo())
                        .outcome(Outcome.SUCCEEDED).build();
            }).when(refundResolution).resolve(any());
            doAnswer(failFirstAttempt).when(purchaseCounts).release(any(), any());
        } else {
            doAnswer(failFirstAttempt).when(gateway).refund(any());
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> userCancellation.cancelMyReservation(
                    "owner", fixture.reservationId(), request(fixture.ticketIds().subList(0, 3))
            ));
            assertThat(gatewayEntered.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<?> second = pool.submit(() -> {
                secondStarted.countDown();
                adminCancellation.cancel(fixture.reservationId(), request(fixture.ticketIds().subList(0, 3)));
            });
            try {
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(3, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(ReservationCancellationInProgressException.class);
                assertThat(first.isDone()).isFalse();
            } finally {
                failGateway.countDown();
            }
            assertThatThrownBy(() -> first.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        }
        adminCancellation.cancel(fixture.reservationId(), request(fixture.ticketIds().subList(0, 3)));
        ArgumentCaptor<GatewayCardPaymentRefundRequest> requests = ArgumentCaptor.forClass(GatewayCardPaymentRefundRequest.class);
        verify(gateway, times(localFailure ? 1 : 2)).refund(requests.capture());
        assertThat(requests.getAllValues()).extracting(GatewayCardPaymentRefundRequest::getPaymentRefundProcessId)
                .containsOnly(requests.getAllValues().getFirst().getPaymentRefundProcessId());
        assertThat(processes.count()).isEqualTo(1);
        assertResult(fixture, 30000, 3);
        verify(purchaseCounts, times(localFailure ? 2 : 1)).release(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void secondCancellationIsRejectedWhileFirstRequestHoldsReservationLock(boolean overlapping) throws Exception {
        Fixture fixture = fixture(false);
        CountDownLatch gatewayEntered = new CountDownLatch(1);
        CountDownLatch finishGateway = new CountDownLatch(1);
        doAnswer(invocation -> {
            gatewayEntered.countDown();
            if (!finishGateway.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("PG 대기 시간 초과");
            return null;
        }).when(gateway).refund(any());

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> userCancellation.cancelMyReservation(
                    "owner", fixture.reservationId(), request(fixture.ticketIds().subList(0, 3))
            ));
            assertThat(gatewayEntered.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<?> second = pool.submit(() -> {
                secondStarted.countDown();
                adminCancellation.cancel(fixture.reservationId(), request(
                        overlapping ? fixture.ticketIds().subList(1, 4) : fixture.ticketIds().subList(3, 4)
                ));
            });
            try {
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(3, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(ReservationCancellationInProgressException.class);
                assertThat(first.isDone()).isFalse();
            } finally {
                finishGateway.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
        }
        assertResult(fixture, 30000, 3);
        verify(gateway, times(1)).refund(any());
        // 첫 요청 종료 후 재요청하면 최신 티켓 상태를 검증한다.
        if (overlapping) {
            assertThatThrownBy(() -> adminCancellation.cancel(fixture.reservationId(), request(fixture.ticketIds().subList(1, 4))))
                    .isInstanceOf(IllegalArgumentException.class);
        } else {
            adminCancellation.cancel(fixture.reservationId(), request(fixture.ticketIds().subList(3, 4)));
        }
        assertResult(fixture, overlapping ? 30000 : 40000, overlapping ? 3 : 4);
        verify(gateway, times(overlapping ? 1 : 2)).refund(any());
        verify(purchaseCounts, times(overlapping ? 1 : 2)).release(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void userCancellationAndManualCompletionShareTheReservationLock(boolean userFirst) throws Exception {
        Fixture fixture = fixture(true);
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch finishApplying = new CountDownLatch(1);
        doAnswer(invocation -> {
            applying.countDown();
            if (!finishApplying.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("로컬 반영 대기 시간 초과");
            return null;
        }).when(purchaseCounts).release(any(), any());
        Runnable user = () -> userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds().subList(0, 3))
        );
        Runnable admin = () -> refundService.completeLocal(fixture.processId());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(userFirst ? user : admin);
            assertThat(applying.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<?> second = pool.submit(() -> {
                secondStarted.countDown();
                (userFirst ? admin : user).run();
            });
            try {
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(3, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(ReservationCancellationInProgressException.class);
                assertThat(first.isDone()).isFalse();
            } finally {
                finishApplying.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
        }
        assertResult(fixture, 30000, 3);
        assertThat(processes.findById(fixture.processId()).orElseThrow().getStatus())
                .isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        verify(purchaseCounts).release(any(), any());
        verify(gateway, times(0)).refund(any());
    }


    @Test
    void knownPgSuccessRecoversOriginalTicketsInsteadOfNewDisjointSelection() {
        Fixture fixture = fixture(true);
        CancelReservationResponse response = userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds().subList(3, 4))
        );

        assertThat(response.previousRequestRecovered()).isTrue();
        assertThat(response.cancelledTicketIds()).containsExactlyElementsOf(fixture.ticketIds().subList(0, 3));
        assertThat(processes.findById(fixture.processId()).orElseThrow().getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        assertResult(fixture, 30000, 3);
        assertThat(tickets.findById(fixture.ticketIds().getLast()).orElseThrow().getStatus()).isEqualTo(TicketStatus.PAID);
        verify(gateway, times(0)).refund(any());
        verify(refundResolution, times(0)).resolve(any());
    }

    @Test
    void automaticRecoveryRollbackKeepsPgSuccessAndCanResumeWithoutAnotherRefund() {
        Fixture fixture = fixture(true);
        doAnswer(invocation -> { throw new IllegalStateException("로컬 반영 실패"); })
                .when(purchaseCounts).release(any(), any());

        assertThatThrownBy(() -> userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds())
        )).isInstanceOf(IllegalStateException.class).hasMessage("로컬 반영 실패");

        assertResult(fixture, 0, 0);
        assertThat(processes.findById(fixture.processId()).orElseThrow().getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_FAILED);
        doAnswer(invocation -> null).when(purchaseCounts).release(any(), any());

        CancelReservationResponse response = userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds())
        );
        assertThat(response.previousRequestRecovered()).isTrue();
        assertThat(response.paymentRefundProcessId()).isEqualTo(fixture.processId());
        assertThat(response.cancelledTicketIds()).containsExactlyElementsOf(fixture.ticketIds().subList(0, 3));
        assertResult(fixture, 30000, 3);
        assertThat(processes.count()).isEqualTo(1);
        verify(gateway, times(0)).refund(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reservationLockProtectsProcessAndSnapshotSurvivesRollback(boolean existing) throws Exception {
        Fixture fixture = fixture(existing);
        CountDownLatch localEntered = new CountDownLatch(1);
        CountDownLatch finishLocal = new CountDownLatch(1);
        doAnswer(invocation -> {
            localEntered.countDown();
            if (!finishLocal.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("로컬 대기 시간 초과");
            throw new IllegalStateException("로컬 반영 실패");
        }).when(purchaseCounts).release(any(), any());

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> userCancellation.cancelMyReservation(
                    "owner", fixture.reservationId(), request(fixture.ticketIds().subList(0, 3))
            ));
            try {
                assertThat(localEntered.await(10, TimeUnit.SECONDS)).isTrue();
                Long processId = processes.findAll().getFirst().getPaymentRefundProcessId();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    PaymentRefundProcess persisted = processes.findById(processId).orElseThrow();
                    assertThat(persisted.getRefund().getSnapshot().getTickets()).hasSize(3);
                });
                CountDownLatch readerStarted = new CountDownLatch(1);
                Future<?> reader = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    readerStarted.countDown();
                    reservations.findByIdForUpdate(fixture.reservationId()).orElseThrow();
                    return processes.findById(processId).orElseThrow().getPaymentRefundProcessId();
                }));
                assertThat(readerStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> reader.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                finishLocal.countDown();
                reader.get(15, TimeUnit.SECONDS);
            } finally {
                finishLocal.countDown();
            }
            assertThatThrownBy(() -> first.get(15, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
        }
        assertResult(fixture, 0, 0);
        assertThat(processes.count()).isEqualTo(1);
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_FAILED);
    }

    @Test
    void pgLookupRecoversWhenRollbackResultRecordingWasLost() {
        Fixture fixture = fixture(false);
        doAnswer(invocation -> { throw new IllegalStateException("로컬 반영 실패"); })
                .when(purchaseCounts).release(any(), any());
        doAnswer(invocation -> { throw new IllegalStateException("결과 기록 실패"); })
                .when(rollbackRecorder).record(any());

        assertThatThrownBy(() -> userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds().subList(0, 3))
        )).isInstanceOf(IllegalStateException.class).hasMessage("로컬 반영 실패");
        assertResult(fixture, 0, 0);
        Long processId = processes.findAll().getFirst().getPaymentRefundProcessId();
        assertThat(processes.findById(processId).orElseThrow().getStatus()).isEqualTo(PaymentRefundProcessStatus.REQUESTED);
        doAnswer(invocation -> null).when(purchaseCounts).release(any(), any());
        doAnswer(invocation -> GatewayRefundResolutionResponse.builder()
                .paymentRefundProcessId(processId).paymentNo("PAY-LOCK").outcome(Outcome.SUCCEEDED).build())
                .when(refundResolution).resolve(any());

        CancelReservationResponse response = userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds().subList(0, 3))
        );
        assertThat(response.paymentRefundProcessId()).isEqualTo(processId);
        assertThat(processes.count()).isEqualTo(1);
        assertResult(fixture, 30000, 3);
        verify(gateway, times(1)).refund(any());
    }

    @Test
    void changedRequestKeepsNewSnapshotAndClosesOldIdAfterOuterRollback() {
        Fixture fixture = fixture(false);
        Long oldId = new TransactionTemplate(transactionManager).execute(status -> {
            Payment payment = payments.findById(fixture.paymentId()).orElseThrow();
            PaymentRefundProcess previous = PaymentRefundProcess.create(
                    payment, tickets.findAllByTicketIdIn(fixture.ticketIds().subList(0, 2)), 20000, 0, false, null
            );
            previous.gatewayFailed("이전 요청 실패");
            return processes.saveAndFlush(previous).getPaymentRefundProcessId();
        });
        doAnswer(invocation -> GatewayRefundResolutionResponse.builder()
                .paymentRefundProcessId(oldId).paymentNo("PAY-LOCK").outcome(Outcome.CLOSED).build())
                .when(refundResolution).resolve(any());
        doAnswer(invocation -> { throw new IllegalStateException("로컬 반영 실패"); })
                .when(purchaseCounts).release(any(), any());

        assertThatThrownBy(() -> userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds())
        )).isInstanceOf(IllegalStateException.class).hasMessage("로컬 반영 실패");
        assertResult(fixture, 0, 0);
        assertThat(processes.count()).isEqualTo(2);
        assertThat(processes.findById(oldId).orElseThrow().getStatus()).isEqualTo(PaymentRefundProcessStatus.SUPERSEDED);
        Long newId = processes.findAll().stream().map(PaymentRefundProcess::getPaymentRefundProcessId)
                .filter(id -> !id.equals(oldId)).findFirst().orElseThrow();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            PaymentRefundProcess created = processes.findById(newId).orElseThrow();
            assertThat(created.getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_FAILED);
            assertThat(created.getRefund().getSnapshot().getTickets()).hasSize(4);
            assertThat(created.getRefundAmount()).isEqualTo(40000);
        });
        doAnswer(invocation -> null).when(purchaseCounts).release(any(), any());
        CancelReservationResponse response = userCancellation.cancelMyReservation(
                "owner", fixture.reservationId(), request(fixture.ticketIds())
        );
        assertThat(response.paymentRefundProcessId()).isEqualTo(newId);
        assertResult(fixture, 40000, 4);
        verify(gateway, times(1)).refund(any());
        verify(refundResolution, times(1)).resolve(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rollbackAfterCompletionRestoresLocalDataAndRetryDoesNotRefundAgain(boolean admin) {
        Fixture fixture = fixture(false);
        CancelReservationRequest request = request(fixture.ticketIds().subList(0, 3));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CancelReservationResponse response = admin
                    ? adminCancellation.cancel(fixture.reservationId(), request)
                    : userCancellation.cancelMyReservation("owner", fixture.reservationId(), request);

            // 응답을 만들 때에는 티켓·좌석과 성공 이력·완료 상태가 모두 A에 반영되어 있어야 한다.
            assertResult(fixture, 30000, 3);
            PaymentRefundProcess process = processes.findById(response.paymentRefundProcessId()).orElseThrow();
            assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
            assertThat(refundHistories.existsByPaymentRefundProcess(process)).isTrue();
            assertThat(response.cancelledTicketIds()).containsExactlyElementsOf(request.getSelectedTicketIdList());

            // 완료 상태를 명시적으로 저장했어도 A의 커밋이 실패하면 로컬 변경은 함께 롤백된다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("완료 상태 반영 후 커밋 실패");
                }
            });
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("커밋 실패");

        assertResult(fixture, 0, 0);
        PaymentRefundProcess pending = processes.findAll().getFirst();
        Long processId = pending.getPaymentRefundProcessId();
        assertThat(pending.getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_FAILED);
        assertThat(refundHistories.existsByPaymentRefundProcess(pending)).isFalse();

        CancelReservationResponse retried = admin
                ? adminCancellation.cancel(fixture.reservationId(), request)
                : userCancellation.cancelMyReservation("owner", fixture.reservationId(), request);
        assertThat(retried.paymentRefundProcessId()).isEqualTo(processId);
        assertThat(processes.count()).isEqualTo(1);
        assertThat(processes.findById(processId).orElseThrow().getStatus())
                .isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        assertResult(fixture, 30000, 3);
        verify(gateway, times(1)).refund(any());
    }

    private Fixture fixture(boolean gatewaySucceeded) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Event event = events.save(Event.builder()
                    .artistName("공연자").title("공연").venue("공연장")
                    .eventDateTime(LocalDateTime.now().plusMonths(1))
                    .totalSeats(4).availableSeats(0).status(EventStatus.ON_SALE).maxTicketsPerPerson(4)
                    .build());
            Reservation reservation = reservations.save(Reservation.builder()
                    .orderId("cancel-lock").userId("owner").event(event)
                    .status(ReservationStatus.PAID).reservedAt(LocalDateTime.now()).build());
            List<Ticket> booked = new ArrayList<>();
            for (int number = 1; number <= 4; number++) {
                Seat seat = seats.save(Seat.builder().event(event).zone("A").seatRow(1).seatCol(number)
                        .grade(SeatGrade.VIP).price(10000).status(SeatStatus.RESERVED).build());
                booked.add(tickets.save(Ticket.builder().reservation(reservation).event(event).seat(seat)
                        .userId("owner").status(TicketStatus.PAID).build()));
            }
            Payment payment = payments.saveAndFlush(Payment.builder().reservation(reservation)
                    .paymentNo("PAY-LOCK").idempotencyKey("cancel-lock")
                    .requestedAt(LocalDateTime.now())
                    .method(PaymentMethod.CREDIT_CARD).status(PaymentStatus.PAID).amount(40000)
                    .cardInfo(CardPaymentInfo.builder().transactionId("TX-LOCK").build()).build());
            Long processId = null;
            if (gatewaySucceeded) {
                PaymentRefundProcess process = PaymentRefundProcess.create(payment, booked.subList(0, 3), 30000, 0, false, null);
                process.gatewaySucceeded();
                processId = processes.saveAndFlush(process).getPaymentRefundProcessId();
            }
            return Fixture.builder()
                    .reservationId(reservation.getReservationId())
                    .paymentId(payment.getPaymentId())
                    .processId(processId)
                    .ticketIds(booked.stream().map(Ticket::getTicketId).toList())
                    .build();
        });
    }

    private void assertResult(Fixture fixture, int refunded, int cancelledTickets) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(payments.findById(fixture.paymentId()).orElseThrow().getRefundedAmount()).isEqualTo(refunded);
            List<Ticket> loaded = tickets.findAllByTicketIdIn(fixture.ticketIds());
            assertThat(loaded.stream().filter(ticket -> ticket.getStatus() == TicketStatus.CANCELLED).count())
                    .isEqualTo(cancelledTickets);
            assertThat(loaded.stream().filter(ticket -> ticket.getSeat().getStatus() == SeatStatus.AVAILABLE).count())
                    .isEqualTo(cancelledTickets);
        });
    }

    private CancelReservationRequest request(List<Long> ids) {
        return CancelReservationRequest.builder().selectedTicketIdList(ids).build();
    }

    @Builder
    private record Fixture(Long reservationId, Long paymentId, Long processId, List<Long> ticketIds) {
    }
}
