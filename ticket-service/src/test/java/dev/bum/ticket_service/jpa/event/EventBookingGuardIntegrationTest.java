package dev.bum.ticket_service.jpa.event;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.service.event.event.EventBookingGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class EventBookingGuardIntegrationTest {
    @Autowired EventJpaRepository events;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    private final LocalDateTime end = LocalDateTime.of(2026, 10, 30, 23, 59, 59);
    private EventBookingGuard guard(LocalDateTime at) {
        return new EventBookingGuard(events, em, Clock.fixed(at.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul")));
    }
    private Event fixture() {
        return Event.builder().artistName("artist").title("event").venue("venue")
                .eventDateTime(LocalDateTime.of(2026, 10, 31, 18, 0))
                .saleStartAt(end.minusDays(1)).saleEndAt(end).status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4).totalSeats(100).availableSeats(100).build();
    }
    @Test void held_seats_do_not_allow_booking_after_the_configured_deadline() {
        Event event = events.saveAndFlush(fixture());
        guard(end).requireBookable(event.getEventId(), true);
        assertThatThrownBy(() -> guard(end.plusSeconds(1)).requireBookable(event.getEventId(), true))
                .hasMessage("판매가 종료된 공연입니다.");
    }
    @Test void read_lock_refreshes_an_already_loaded_event_before_validation() {
        Event event = events.saveAndFlush(fixture());
        em.createNativeQuery("update events set status = 'CANCELLED' where event_id = :id")
                .setParameter("id", event.getEventId()).executeUpdate();
        assertThat(event.getStatus()).isEqualTo(EventStatus.ON_SALE);
        assertThatThrownBy(() -> guard(end.minusHours(1)).requireBookable(event.getEventId(), true))
                .hasMessageContaining("공연 상태");
    }
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void event_cancellation_waits_for_a_booking_read_lock_then_new_bookings_are_rejected() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        Long id = tx.execute(status -> events.saveAndFlush(fixture()).getEventId());
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> booking = executor.submit(() -> tx.executeWithoutResult(status -> {
                guard(end.minusHours(1)).requireBookable(id, true);
                held.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("락 테스트 시간 초과"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(held.await(3, TimeUnit.SECONDS)).isTrue();
            Future<?> cancellation = executor.submit(() -> tx.executeWithoutResult(status -> {
                events.findByIdForUpdate(id).orElseThrow().changeStatus(EventStatus.CANCELLED);
                changed.countDown();
            }));
            assertThat(changed.await(200, TimeUnit.MILLISECONDS)).isFalse();
            release.countDown();
            booking.get(5, TimeUnit.SECONDS);
            cancellation.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                    guard(end.minusHours(1)).requireBookable(id, true))).hasMessageContaining("공연 상태");
        } finally {
            release.countDown(); executor.shutdownNow();
            tx.executeWithoutResult(status -> events.deleteById(id));
        }
    }
}
