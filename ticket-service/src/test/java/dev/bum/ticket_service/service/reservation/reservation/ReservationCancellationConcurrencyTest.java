package dev.bum.ticket_service.service.reservation.reservation;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.feign.paymentgateway.GatewayCardPaymentRefundRequest;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.payment.card.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepositoryImpl;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.ticket_service.service.payment.refund.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.refund.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReservationRepositoryImpl.class, ReservationService.class, ReservationManagementService.class,
        PaymentRefundProcessService.class, CardPaymentRefundService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservationCancellationConcurrencyTest {
    @Autowired ReservationService userCancellation;
    @Autowired ReservationManagementService adminCancellation;
    @Autowired PaymentRefundProcessService refundService;
    @Autowired ReservationJpaRepository reservations;
    @Autowired PaymentJpaRepository payments;
    @Autowired PaymentRefundProcessJpaRepository processes;
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void waitingRequestRetriesTheSameProcessAfterTheFirstCancellationRollsBack(boolean localFailure) throws Exception {
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
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                failGateway.countDown();
            }
            assertThatThrownBy(() -> first.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
            second.get(15, TimeUnit.SECONDS);
        }
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
    void secondCancellationWaitsAndRevalidatesTickets(boolean overlapping) throws Exception {
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
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                finishGateway.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            if (overlapping) {
                assertThatThrownBy(() -> second.get(15, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class);
            } else {
                second.get(15, TimeUnit.SECONDS);
            }
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
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                finishApplying.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class);
        }
        assertResult(fixture, 30000, 3);
        assertThat(processes.findById(fixture.processId()).orElseThrow().getStatus())
                .isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        verify(purchaseCounts).release(any(), any());
        verify(gateway, times(0)).refund(any());
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
            return new Fixture(reservation.getReservationId(), payment.getPaymentId(), processId,
                    booked.stream().map(Ticket::getTicketId).toList());
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

    private record Fixture(Long reservationId, Long paymentId, Long processId, List<Long> ticketIds) {
    }
}
