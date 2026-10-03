package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException;
import dev.bum.ticket_service.exception.seat.SeatOccupationFailedException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(SeatCacheMissOccupationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SeatCacheMissOccupationIntegrationTest {
    @Autowired private SeatCacheMissOccupationService service;
    @Autowired private SeatJpaRepository seats;
    @Autowired private EventJpaRepository events;
    @Autowired private AreaJpaRepository areas;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private StringRedisTemplate redis;

    @Test
    @DisplayName("DB 좌석 락은 Redis 스크립트가 끝나고 트랜잭션이 종료될 때 해제된다")
    void database_lock_is_held_until_redis_occupation_finishes() throws Exception {
        Fixture fixture = fixture(SeatStatus.AVAILABLE);
        CountDownLatch scriptEntered = new CountDownLatch(1);
        CountDownLatch finishScript = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        given(redis.execute(SeatOccupationScripts.OCCUPY, fixture.keys(), "LOCKED:user:order", "600000", "1"))
                .willAnswer(invocation -> {
                    scriptEntered.countDown();
                    if (!finishScript.await(5, TimeUnit.SECONDS)) throw new AssertionError("Redis 처리 대기 시간 초과");
                    return 1L;
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> occupation = executor.submit(() -> occupy(fixture));
            assertThat(scriptEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> update = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                updateStarted.countDown();
                seats.findByIdForUpdate(fixture.seatId()).orElseThrow().reserved();
            }));
            assertThat(updateStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> update.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            finishScript.countDown();
            occupation.get(5, TimeUnit.SECONDS);
            update.get(5, TimeUnit.SECONDS);
            assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.RESERVED);
        } finally {
            finishScript.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("DB에서 RESERVED인 좌석은 Redis를 호출하지 않고 거절한다")
    void reserved_database_seat_is_rejected() {
        Fixture fixture = fixture(SeatStatus.RESERVED);
        assertThatThrownBy(() -> occupy(fixture)).isInstanceOf(SeatAlreadyOccupiedException.class);
        then(redis).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시 누락 시 요청 좌표와 DB 좌석 정보가 다르면 거절한다")
    void mismatched_coordinates_are_rejected() {
        Fixture fixture = fixture(SeatStatus.AVAILABLE);
        SeatInfo invalid = SeatInfo.builder().id(fixture.seatId()).zone("VIP").row(1).col(2).build();
        assertThatThrownBy(() -> service.occupy(fixture.eventId(), invalid, fixture.key(),
                "LOCKED:user:order", Duration.ofMinutes(10))).isInstanceOf(SeatOccupationFailedException.class);
        then(redis).shouldHaveNoInteractions();
    }

    private void occupy(Fixture fixture) {
        service.occupy(fixture.eventId(), SeatInfo.builder().id(fixture.seatId()).zone("VIP").row(1).col(1).build(),
                fixture.key(), "LOCKED:user:order", Duration.ofMinutes(10));
    }

    private Fixture fixture(SeatStatus status) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Event event = events.save(Event.builder().artistName("가수").title("공연").venue("공연장")
                    .eventDateTime(LocalDateTime.now().plusDays(1)).totalSeats(1).availableSeats(1)
                    .status(EventStatus.ON_SALE).maxTicketsPerPerson(1).build());
            Area area = areas.save(Area.builder().event(event).areaName("VIP").layoutKey("VIP")
                    .grade(SeatGrade.VIP).price(10000).status(AreaStatus.ACTIVE).build());
            Seat seat = seats.save(Seat.builder().event(event).area(area).zone("VIP").seatRow(1).seatCol(1)
                    .grade(SeatGrade.VIP).price(10000).status(status).build());
            return new Fixture(event.getEventId(), seat.getSeatId());
        });
    }

    private record Fixture(Long eventId, Long seatId) {
        String key() { return "event:" + eventId + ":seat:VIP:1:1"; }
        List<String> keys() { return List.of(key(), key() + ":lock"); }
    }
}

