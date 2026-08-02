package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeatCacheServiceTest {

    private static final String USER_ID = "user01";
    private static final Long EVENT_ID = 1L;
    private static final String PURCHASE_LIMIT_KEY = "user:purchase:limit:event:1:user01";
    private static final String FIRST_SEAT_KEY = "event:1:seat:VIP:1:1";
    private static final String SECOND_SEAT_KEY = "event:1:seat:VIP:1:2";

    @Mock
    private SeatRepository repository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private StringRedisTemplate seatRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SeatCacheService seatCacheService;

    @BeforeEach
    void setUp() {
        seatCacheService = new SeatCacheService(repository, eventRepository, seatRedisTemplate);
        given(seatRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("다중 좌석 선점 중 일부 락 획득 실패 시 이미 잡은 Redis 락과 상태를 롤백한다")
    void occupy_seat_rolls_back_acquired_locks_when_later_lock_fails() {
        SeatOccupyRequest request = SeatOccupyRequest.builder()
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .maxTicketsPerPerson(4)
                .seats(List.of(
                        SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build(),
                        SeatInfo.builder().id(2L).zone("VIP").row(1).col(2).build()
                ))
                .build();

        given(eventRepository.selectById(EVENT_ID)).willReturn(event());
        given(valueOperations.get(PURCHASE_LIMIT_KEY)).willReturn(null);
        given(valueOperations.get(FIRST_SEAT_KEY)).willReturn(SeatStatus.AVAILABLE.name());
        given(valueOperations.get(SECOND_SEAT_KEY)).willReturn(SeatStatus.AVAILABLE.name());
        given(valueOperations.setIfAbsent(eq(FIRST_SEAT_KEY + ":lock"), anyString(), any(Duration.class))).willReturn(true);
        given(valueOperations.setIfAbsent(eq(SECOND_SEAT_KEY + ":lock"), anyString(), any(Duration.class))).willReturn(false);

        assertThatThrownBy(() -> seatCacheService.occupySeat(request))
                .isInstanceOf(SeatAlreadyOccupiedException.class);

        then(valueOperations).should().set(eq(FIRST_SEAT_KEY), eq(SeatStatus.AVAILABLE.name()), any(Duration.class));
        then(seatRedisTemplate).should().delete(List.of(FIRST_SEAT_KEY + ":lock"));
        then(valueOperations).should(never()).set(eq(SECOND_SEAT_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("좌석 Redis 상태 동기화는 트랜잭션 커밋 이후에만 실행된다")
    void sync_reserved_seats_registers_after_commit_callback() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            Seat seat = seat(1L, "VIP", 1, 1);

            seatCacheService.syncReservedSeatsAfterCommit(List.of(seat));

            then(valueOperations).should(never()).set(anyString(), anyString(), any(Duration.class));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            then(valueOperations).should().set(eq(FIRST_SEAT_KEY), eq(SeatStatus.RESERVED.name()), any(Duration.class));
            then(seatRedisTemplate).should().delete(FIRST_SEAT_KEY + ":lock");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Event event() {
        return Event.builder()
                .eventId(EVENT_ID)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.now().plusDays(30))
                .runningMinutes(120)
                .totalSeats(100)
                .availableSeats(100)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private Seat seat(Long seatId, String zone, Integer row, Integer col) {
        return Seat.builder()
                .seatId(seatId)
                .event(event())
                .zone(zone)
                .seatRow(row)
                .seatCol(col)
                .grade(SeatGrade.VIP)
                .price(180000)
                .status(SeatStatus.AVAILABLE)
                .build();
    }
}
