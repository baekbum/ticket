package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException;
import dev.bum.ticket_service.exception.seat.SeatOccupationFailedException;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SeatCacheServiceTest {

    private static final String USER_ID = "user01";
    private static final Long EVENT_ID = 1L;
    private static final String GROUP_PURCHASE_LIMIT_KEY = "user:purchase:limit:group:IU_2026_HEREH_ENCORE:user01";
    private static final String FIRST_SEAT_KEY = "event:1:seat:VIP:1:1";
    private static final String SECOND_SEAT_KEY = "event:1:seat:VIP:1:2";

    @Mock
    private SeatRepository repository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private StringRedisTemplate seatRedisTemplate;

    @Mock
    private SeatCacheSyncFailureService seatCacheSyncFailureService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SeatCacheService seatCacheService;

    @BeforeEach
    void setUp() {
        seatCacheService = new SeatCacheService(
                repository,
                eventRepository,
                seatRedisTemplate,
                seatCacheSyncFailureService
        );
        lenient().when(seatRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("판매 시작 전 덮어쓰기는 DB 좌석 상태를 Redis에 그대로 반영한다")
    void warm_up_before_sale_uses_database_status() {
        Event event = Event.builder().eventId(EVENT_ID).saleStartAt(LocalDateTime.now().plusHours(1)).build();
        Seat reserved = warmUpSeat(event, SeatStatus.RESERVED);
        given(eventRepository.selectById(EVENT_ID)).willReturn(event);
        given(repository.selectByEventId(EVENT_ID)).willReturn(List.of(reserved));

        seatCacheService.warmUpEventSeatsToCache(EVENT_ID, dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode.OVERWRITE);

        then(valueOperations).should().multiSet(java.util.Map.of(FIRST_SEAT_KEY, "RESERVED"));
    }

    @Test
    @DisplayName("판매 시작 후 덮어쓰기는 좌석 캐시를 변경하지 않는다")
    void warm_up_after_sale_rejects_overwrite() {
        Event event = Event.builder().eventId(EVENT_ID).saleStartAt(LocalDateTime.now().minusMinutes(1)).build();
        given(eventRepository.selectById(EVENT_ID)).willReturn(event);
        given(repository.selectByEventId(EVENT_ID)).willReturn(List.of(warmUpSeat(event, SeatStatus.AVAILABLE)));

        assertThatThrownBy(() -> seatCacheService.warmUpEventSeatsToCache(
                EVENT_ID, dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode.OVERWRITE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("판매 시작 이후");
        then(valueOperations).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("구역 단위 예열도 판매 시작 후 덮어쓰기를 거부한다")
    void area_warm_up_after_sale_rejects_overwrite() {
        Event event = Event.builder().eventId(EVENT_ID).saleStartAt(LocalDateTime.now().minusMinutes(1)).build();
        given(repository.selectByAreaId(10L)).willReturn(List.of(warmUpSeat(event, SeatStatus.AVAILABLE)));
        given(eventRepository.selectById(EVENT_ID)).willReturn(event);

        assertThatThrownBy(() -> seatCacheService.warmUpAreaSeatsToCache(
                10L, dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode.OVERWRITE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("판매 시작 이후");
        then(valueOperations).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("판매 시작 후 예열은 DB에서 가능한 좌석만 Redis 좌석 키와 lock 키가 비었을 때 채운다")
    void warm_up_after_sale_only_fills_missing_available_seats() {
        Event event = Event.builder().eventId(EVENT_ID).saleStartAt(LocalDateTime.now().minusMinutes(1)).build();
        Seat available = warmUpSeat(event, SeatStatus.AVAILABLE);
        Seat reserved = Seat.builder().event(event).zone("VIP").seatRow(1).seatCol(2)
                .status(SeatStatus.RESERVED).build();
        given(eventRepository.selectById(EVENT_ID)).willReturn(event);
        given(repository.selectByEventId(EVENT_ID)).willReturn(List.of(available, reserved));
        given(seatRedisTemplate.execute(any(DefaultRedisScript.class),
                eq(List.of(FIRST_SEAT_KEY, FIRST_SEAT_KEY + ":lock")), eq("604800000")))
                .willReturn(1L);

        String result = seatCacheService.warmUpEventSeatsToCache(
                EVENT_ID, dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode.MISSING_ONLY);

        assertThat(result).contains("반영 1개");
        then(seatRedisTemplate).should().execute(any(DefaultRedisScript.class),
                eq(List.of(FIRST_SEAT_KEY, FIRST_SEAT_KEY + ":lock")), eq("604800000"));
        then(valueOperations).shouldHaveNoInteractions();
    }

    private Seat warmUpSeat(Event event, SeatStatus status) {
        return Seat.builder().event(event).zone("VIP").seatRow(1).seatCol(1).status(status).build();
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

        Event event = event();
        given(eventRepository.selectById(EVENT_ID)).willReturn(event);
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
    @DisplayName("선택 매수가 제한 이내이면 기존 예매 매수와 구매 캐시를 조회하지 않고 선점한다")
    void occupy_seat_ignores_stale_group_purchase_cache() {
        SeatOccupyRequest request = SeatOccupyRequest.builder()
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .maxTicketsPerPerson(1)
                .seats(List.of(
                        SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build()
                ))
                .build();

        Event event = event(TicketLimitScope.PER_GROUP, 1);
        given(eventRepository.selectById(EVENT_ID)).willReturn(event);
        lenient().when(valueOperations.get(GROUP_PURCHASE_LIMIT_KEY)).thenReturn("99");
        given(valueOperations.get(FIRST_SEAT_KEY)).willReturn("AVAILABLE");
        given(valueOperations.setIfAbsent(eq(FIRST_SEAT_KEY + ":lock"), anyString(), any(Duration.class))).willReturn(true);

        assertThat(seatCacheService.occupySeat(request).getOrderId()).isNotBlank();

        then(valueOperations).should(never()).get(GROUP_PURCHASE_LIMIT_KEY);
        then(ticketRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("좌석 선점은 기존 예매 매수를 조회하지 않고 이번 선택 매수만 제한한다")
    void occupy_seat_checks_only_selected_count() {
        Event event = event(TicketLimitScope.PER_GROUP, 1);
        given(eventRepository.selectById(EVENT_ID)).willReturn(event);
        SeatOccupyRequest request = SeatOccupyRequest.builder().eventId(EVENT_ID).userId(USER_ID)
                .seats(List.of(SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build(),
                        SeatInfo.builder().id(2L).zone("VIP").row(1).col(2).build())).build();
        assertThatThrownBy(() -> seatCacheService.occupySeat(request))
                .isInstanceOf(TicketLimitExceededException.class);
        then(ticketRepository).shouldHaveNoInteractions();
        then(valueOperations).shouldHaveNoInteractions();
    }
    @Test
    @DisplayName("좌석 선점 중 Redis 장애는 복구 단서 로깅 후 선점 실패로 변환한다")
    void occupy_seat_wraps_redis_error() {
        SeatOccupyRequest request = SeatOccupyRequest.builder()
                .eventId(EVENT_ID)
                .userId(USER_ID)
                .seats(List.of(
                        SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build()
                ))
                .build();
        Event event = event();

        given(eventRepository.selectById(EVENT_ID)).willReturn(event);
        given(valueOperations.get(FIRST_SEAT_KEY)).willThrow(new DataAccessException("redis error") {});

        assertThatThrownBy(() -> seatCacheService.occupySeat(request))
                .isInstanceOf(SeatOccupationFailedException.class)
                .hasMessage("잠시 후 다시 시도해주세요.");

        then(valueOperations).should().get(FIRST_SEAT_KEY);
        then(valueOperations).should(never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("좌석 선점 검증은 선택 좌석 중 가장 먼저 만료되는 시각을 반환한다")
    void validate_occupied_seat_returns_earliest_expiration() {
        List<SeatInfo> seats = List.of(
                SeatInfo.builder().id(1L).zone("VIP").row(1).col(1).build(),
                SeatInfo.builder().id(2L).zone("VIP").row(1).col(2).build()
        );
        String lockValue = "LOCKED:user01:order-1";
        given(valueOperations.get(FIRST_SEAT_KEY)).willReturn(lockValue);
        given(valueOperations.get(SECOND_SEAT_KEY)).willReturn(lockValue);
        given(seatRedisTemplate.getExpire(FIRST_SEAT_KEY, TimeUnit.MILLISECONDS)).willReturn(540_000L);
        given(seatRedisTemplate.getExpire(SECOND_SEAT_KEY, TimeUnit.MILLISECONDS)).willReturn(300_000L);
        LocalDateTime beforeValidation = LocalDateTime.now();

        LocalDateTime expiresAt = seatCacheService.validateOccupiedSeat(
                EVENT_ID,
                USER_ID,
                "order-1",
                seats
        );

        assertThat(expiresAt)
                .isAfterOrEqualTo(beforeValidation.plusSeconds(299))
                .isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(301));
    }

    @Test
    @DisplayName("좌석 Redis 상태 동기화는 트랜잭션 커밋 이후에만 실행된다")
    void sync_reserved_seats_registers_after_commit_callback() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            Seat seat = seat(1L, "VIP", 1, 1);
            given(seatRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), eq("RESERVED")))
                    .willReturn(1L);

            seatCacheService.syncReservedSeatsAfterCommit(List.of(seat));

            then(seatRedisTemplate).should(never()).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            then(seatRedisTemplate).should().execute(any(DefaultRedisScript.class),
                    eq(List.of(FIRST_SEAT_KEY, FIRST_SEAT_KEY + ":lock")), anyString(), eq("RESERVED"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("커밋 후 좌석 Redis 동기화 실패는 보정 이력으로 저장한다")
    void sync_reserved_seats_records_failure_after_commit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            Seat seat = seat(1L, "VIP", 1, 1);
            DataAccessException redisError = new DataAccessException("redis error") {};
            given(seatRedisTemplate.execute(any(DefaultRedisScript.class),
                    eq(List.of(FIRST_SEAT_KEY, FIRST_SEAT_KEY + ":lock")), anyString(), eq("RESERVED")))
                    .willThrow(redisError);

            seatCacheService.syncReservedSeatsAfterCommit(List.of(seat));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            then(seatCacheSyncFailureService).should().recordFailure(
                    eq("syncReservedSeatsAfterCommit"),
                    eq("event:{eventId}:seat"),
                    eq(List.of(FIRST_SEAT_KEY)),
                    eq(List.of(SeatStatus.RESERVED.name())),
                    eq(redisError)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("좌석별 동기화를 계속 수행하고 실패한 좌석만 각각 저장한다")
    void sync_available_seats_continues_and_records_only_each_failed_seat() {
        String secondKey = "event:1:seat:VIP:1:2";
        String thirdKey = "event:1:seat:VIP:1:3";
        String fourthKey = "event:1:seat:VIP:1:4";
        DataAccessException secondError = new DataAccessException("second failed") {};
        DataAccessException thirdError = new DataAccessException("third failed") {};
        given(seatRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), eq("AVAILABLE")))
                .willReturn(1L).willThrow(secondError).willThrow(thirdError).willReturn(1L);

        seatCacheService.syncAvailableSeatsAfterCommit(List.of(
                seat(1L, "VIP", 1, 1), seat(2L, "VIP", 1, 2),
                seat(3L, "VIP", 1, 3), seat(4L, "VIP", 1, 4)));

        then(seatRedisTemplate).should().execute(any(DefaultRedisScript.class),
                eq(List.of(fourthKey, fourthKey + ":lock")), anyString(), eq("AVAILABLE"));
        then(seatCacheSyncFailureService).should().recordFailure("syncAvailableSeatsAfterCommit",
                "event:{eventId}:seat", List.of(secondKey), List.of("AVAILABLE"), secondError);
        then(seatCacheSyncFailureService).should().recordFailure("syncAvailableSeatsAfterCommit",
                "event:{eventId}:seat", List.of(thirdKey), List.of("AVAILABLE"), thirdError);
        then(seatCacheSyncFailureService).shouldHaveNoMoreInteractions();
    }

    private Event event() {
        return event(TicketLimitScope.PER_EVENT);
    }

    private Event event(TicketLimitScope ticketLimitScope) {
        return event(ticketLimitScope, 4);
    }

    private Event event(TicketLimitScope ticketLimitScope, int maxTicketsPerPerson) {
        return Event.builder()
                .eventId(EVENT_ID)
                .artistName("IU")
                .title("IU Concert")
                .eventGroupCode("IU_2026_HEREH_ENCORE")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.now().plusDays(30))
                .runningMinutes(120)
                .totalSeats(100)
                .availableSeats(100)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(maxTicketsPerPerson)
                .ticketLimitScope(ticketLimitScope)
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
