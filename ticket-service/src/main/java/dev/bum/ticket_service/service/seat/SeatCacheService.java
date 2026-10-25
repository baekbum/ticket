package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatRedisEntryResponse;
import dev.bum.common.service.ticket.seat.dto.SeatRedisInspectResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.enums.SeatRedisInspectMode;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException;
import dev.bum.ticket_service.exception.seat.SeatCacheNotFoundException;
import dev.bum.ticket_service.exception.seat.SeatOccupationFailedException;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;

import io.micrometer.observation.annotation.Observed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static dev.bum.common.service.ticket.event.event.enums.TicketLimitScope.PER_GROUP;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheService {

    private static final Duration SEAT_CACHE_TTL = Duration.ofDays(7);
    private static final Duration SEAT_LOCK_TTL = Duration.ofMinutes(10);
    private static final DefaultRedisScript<Long> SAFE_WARM_UP_SCRIPT = safeWarmUpScript();
    private static final DateTimeFormatter ORDER_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SeatRepository repository;
    private final EventRepository eventRepository;
    private final StringRedisTemplate seatRedisTemplate;
    private final SeatCacheSyncOutboxService seatCacheSyncOutboxService;
    private final SeatCacheMissOccupationService seatCacheMissOccupationService;

    /**
     * 공연 단위 좌석 정보를 Redis에 적재하는 메서드
     * @param eventId
     * @param mode
     * @return
     */
    @Observed(name = "ticket.seat-cache.redis.warm-up-event", contextualName = "ticket seat cache redis warm up event")
    public String warmUpEventSeatsToCache(Long eventId, SeatCacheWarmUpMode mode) {
        log.info("[REDIS-WARM-UP] EventId : {}, Mode : {}", eventId, mode);

        Event event = eventRepository.selectById(eventId);
        validateEventSaleStartAt(event);

        List<Seat> seats = repository.selectByEventId(eventId);
        int updated = warmUpSeatCache(seats, mode, event.getSaleStartAt());

        return String.format("이벤트 %d번 좌석 캐시 적재 완료 - 대상 %d개, 반영 %d개", eventId, seats.size(), updated);
    }

    /**
     * 구역 단위 좌석 정보를 Redis에 적재하는 메서드
     * @param areaId
     * @param mode
     * @return
     */
    @Observed(name = "ticket.seat-cache.redis.warm-up-area", contextualName = "ticket seat cache redis warm up area")
    public String warmUpAreaSeatsToCache(Long areaId, SeatCacheWarmUpMode mode) {
        log.info("[REDIS-WARM-UP] AreaId : {}, Mode : {}", areaId, mode);
        List<Seat> seats = repository.selectByAreaId(areaId);
        if (seats.isEmpty()) {
            return String.format("구역 %d번 좌석 캐시 적재 완료 - 대상 0개, 반영 0개", areaId);
        }
        Event event = eventRepository.selectById(seats.get(0).getEvent().getEventId());
        validateEventSaleStartAt(event);
        int updated = warmUpSeatCache(seats, mode, event.getSaleStartAt());
        return String.format("구역 %d번 좌석 캐시 적재 완료 - 대상 %d개, 반영 %d개", areaId, seats.size(), updated);
    }

    /**
     * 공연 단위 좌석 캐시를 Redis에서 삭제하는 메서드
     * @param eventId
     * @return
     */
    @Observed(name = "ticket.seat-cache.redis.delete-event", contextualName = "ticket seat cache redis delete event")
    public String deleteEventSeatsFromCache(Long eventId) {
        log.info("[REDIS-DELETE] EventId : {}", eventId);
        List<Seat> seats = repository.selectByEventId(eventId);
        long deleted = deleteSeatCache(seats);
        return String.format("이벤트 %d번 좌석 캐시 삭제 완료 - 대상 %d개, 삭제 key %d개", eventId, seats.size(), deleted);
    }

    /**
     * 구역 단위 좌석 캐시를 Redis에서 삭제하는 메서드
     * @param areaId
     * @return
     */
    @Observed(name = "ticket.seat-cache.redis.delete-area", contextualName = "ticket seat cache redis delete area")
    public String deleteAreaSeatsFromCache(Long areaId) {
        log.info("[REDIS-DELETE] AreaId : {}", areaId);
        List<Seat> seats = repository.selectByAreaId(areaId);
        long deleted = deleteSeatCache(seats);
        return String.format("구역 %d번 좌석 캐시 삭제 완료 - 대상 %d개, 삭제 key %d개", areaId, seats.size(), deleted);
    }

    /**
     * 단일 좌석을 특정 사용자로 Redis 테스트 선점 처리하는 메서드
     * @return
     */
    public SeatRedisInspectResponse inspectEventSeatCache(Long eventId, String zone, Integer row, Integer col, int limit, SeatRedisInspectMode mode) {
        int normalizedLimit = normalizeInspectLimit(limit);
        SeatRedisInspectMode inspectMode = mode == null ? SeatRedisInspectMode.SEAT : mode;

        List<SeatRedisEntryResponse> entries = scanSeatKeys(buildSeatInspectPattern(eventId, zone, row, col, inspectMode), normalizedLimit)
                .stream()
                .filter(key -> matchesInspectMode(key, inspectMode))
                .limit(normalizedLimit)
                .map(key -> inspectMode == SeatRedisInspectMode.LOCK ? toSeatRedisLockEntry(key) : toSeatRedisEntry(key))
                .collect(Collectors.toList());

        return SeatRedisInspectResponse.builder()
                .scope(inspectMode.name())
                .eventId(eventId)
                .limit(normalizedLimit)
                .count(entries.size())
                .entries(entries)
                .build();
    }

    public String lockSeatCacheForUser(Long seatId, String userId) {
        log.info("[REDIS-TEST-LOCK] SeatId : {}, UserId : {}", seatId, userId);
        Seat seat = repository.selectById(seatId);
        String redisKey = buildSeatRedisKey(seat);
        String value = buildSeatLockValue(userId, generateOrderId());
        SeatInfo info = SeatInfo.builder().id(seatId).zone(seat.getZone())
                .row(seat.getSeatRow()).col(seat.getSeatCol()).build();
        try {
            occupySeatAtomically(seat.getEvent().getEventId(), info, redisKey, value);
        } catch (RuntimeException e) {
            rollbackSeats(List.of(redisKey), value);
            throw e;
        }
        return String.format("좌석 %d번을 %s 사용자로 Redis 테스트 선점 처리했습니다.", seatId, userId);
    }

    /**
     * 단일 좌석 Redis 테스트 선점을 해제하는 메서드
     * @param seatId
     * @return
     */
    public String unlockSeatCache(Long seatId) {
        log.info("[REDIS-TEST-UNLOCK] SeatId : {}", seatId);
        Seat seat = repository.selectById(seatId);
        String redisKey = buildSeatRedisKey(seat);
        String lockKey = redisKey + ":lock";
        String currentStatus = seatRedisTemplate.opsForValue().get(redisKey);

        if (currentStatus == null) {
            throw new SeatCacheNotFoundException();
        }

        if (!currentStatus.startsWith("LOCKED:")) {
            throw new SeatAlreadyOccupiedException("선점 상태가 아닌 좌석입니다.");
        }

        seatRedisTemplate.opsForValue().set(redisKey, "AVAILABLE", SEAT_CACHE_TTL);
        seatRedisTemplate.delete(lockKey);

        return String.format("좌석 %d번의 Redis 테스트 선점을 취소했습니다.", seatId);
    }

    /**
     * Redis를 이용한 다중 좌석 선점 메서드
     * @param eventId
     */
    public String unlockEventSeatCache(Long eventId) {
        log.info("[REDIS-TEST-UNLOCK-EVENT] EventId : {}", eventId);
        List<Seat> seats = repository.selectByEventId(eventId);
        int released = 0;

        for (Seat seat : seats) {
            String redisKey = buildSeatRedisKey(seat);
            String currentStatus = seatRedisTemplate.opsForValue().get(redisKey);

            if (currentStatus != null && currentStatus.startsWith("LOCKED:")) {
                seatRedisTemplate.opsForValue().set(redisKey, SeatStatus.AVAILABLE.name(), SEAT_CACHE_TTL);
                seatRedisTemplate.delete(redisKey + ":lock");
                released++;
            }
        }

        return String.format("이벤트 %d번 좌석 선점 %d건을 취소했습니다.", eventId, released);
    }

    public SeatResponse applyCachedStatus(SeatResponse seat) {
        if (seat == null || seat.getEventId() == null || seat.getZone() == null
                || seat.getSeatRow() == null || seat.getSeatCol() == null) {
            return seat;
        }

        String redisKey = buildSeatRedisKey(seat.getEventId(), seat.getZone(), seat.getSeatRow(), seat.getSeatCol());
        String value = seatRedisTemplate.opsForValue().get(redisKey);

        applyCachedStatusValue(seat, value);
        return seat;
    }

    public List<SeatResponse> applyCachedStatuses(List<SeatResponse> seats) {
        if (seats == null || seats.isEmpty()) {
            return seats;
        }

        List<SeatResponse> cacheableSeats = seats.stream()
                .filter(this::isCacheableSeatResponse)
                .toList();

        if (cacheableSeats.isEmpty()) {
            return seats;
        }

        List<String> redisKeys = cacheableSeats.stream()
                .map(seat -> buildSeatRedisKey(seat.getEventId(), seat.getZone(), seat.getSeatRow(), seat.getSeatCol()))
                .toList();
        List<String> values = seatRedisTemplate.opsForValue().multiGet(redisKeys);

        if (values == null) {
            return seats;
        }

        for (int i = 0; i < cacheableSeats.size(); i++) {
            String value = i < values.size() ? values.get(i) : null;
            applyCachedStatusValue(cacheableSeats.get(i), value);
        }

        return seats;
    }

    private boolean isCacheableSeatResponse(SeatResponse seat) {
        return seat != null && seat.getEventId() != null && seat.getZone() != null
                && seat.getSeatRow() != null && seat.getSeatCol() != null;
    }

    private void applyCachedStatusValue(SeatResponse seat, String value) {
        if (value == null) {
            return;
        }
        if (value.startsWith("LOCKED:")) {
            seat.setStatus(SeatStatus.LOCKED);
            return;
        }
        try {
            seat.setStatus(SeatStatus.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            seat.setStatus(SeatStatus.LOCKED);
        }
    }

    @Observed(name = "ticket.seat-cache.redis.occupy", contextualName = "ticket seat cache redis occupy")
    public SeatOccupyResponse occupySeat(SeatOccupyRequest request) {
        long eventId = request.getEventId();
        String userId = request.getUserId();
        List<SeatInfo> seats = request.getSeats();
        String orderId = generateOrderId();
        String lockValue = buildSeatLockValue(userId, orderId);
        LocalDateTime expiresAt = LocalDateTime.now().plus(SEAT_LOCK_TTL);
        List<String> attemptedRedisKeys = new ArrayList<>();

        try {
            validateUserPurchaseLimit(request);
            for (SeatInfo seat : seats) {
                String redisKey = buildSeatRedisKey(eventId, seat.getZone(), seat.getRow(), seat.getCol());
                // 응답 소실이나 DB 트랜잭션 종료 실패도 본인 잠금만 정리할 수 있도록 먼저 기록한다.
                attemptedRedisKeys.add(redisKey);
                occupySeatAtomically(eventId, seat, redisKey, lockValue);
            }

            return SeatOccupyResponse.builder()
                    .orderId(orderId)
                    .eventId(request.getEventId())
                    .userId(userId)
                    .seats(seats)
                    .expiresAt(expiresAt)
                    .build();
        } catch (SeatCacheNotFoundException | SeatAlreadyOccupiedException
                 | TicketLimitExceededException | SeatOccupationFailedException e) {
            rollbackSeats(attemptedRedisKeys, lockValue);
            throw e;
        } catch (Exception e) {
            rollbackSeats(attemptedRedisKeys, lockValue);
            log.error("[좌석 선점 실패] eventId={}, userId={}, orderId={}, seatKeys={}",
                    eventId, userId, orderId, attemptedRedisKeys, e);
            throw new SeatOccupationFailedException("잠시 후 다시 시도해주세요.", e);
        }
    }

    private void occupySeatAtomically(Long eventId, SeatInfo seat, String redisKey, String lockValue) {
        // Lua에서 상태 확인·잠금 획득·좌석 상태 변경을 원자적으로 실행하고 처리 결과를 반환한다.
        Long result = seatRedisTemplate.execute(SeatOccupationScripts.OCCUPY,
                List.of(redisKey, redisKey + ":lock"), lockValue,
                String.valueOf(SEAT_LOCK_TTL.toMillis()), "0");

        if (Long.valueOf(-1L).equals(result)) {
            // -1: 좌석 캐시와 잠금이 없어 아직 선점하지 않았다. DB 검증 후 Lua로 다시 선점한다.
            seatCacheMissOccupationService.occupy(eventId, seat, redisKey, lockValue, SEAT_LOCK_TTL);
        } else if (Long.valueOf(0L).equals(result)) {
            // 0: 기존 잠금이 있거나 좌석 상태가 AVAILABLE이 아니므로 선점할 수 없다.
            throw new SeatAlreadyOccupiedException("선택하신 좌석 중 이미 선택된 좌석이 포함되어 있습니다.");
        } else if (!Long.valueOf(1L).equals(result)) {
            // null 또는 예상하지 못한 값: 선점 성공을 확인할 수 없으므로 예외로 처리한다.
            throw new DataRetrievalFailureException("좌석 Redis 선점 결과를 확인할 수 없습니다.");
        }
        // 1: 잠금 생성과 좌석 상태 변경이 완료됐다. 위 조건에 해당하지 않아 정상 진행한다.
    }

    /**
     * 예매 요청 좌석이 요청 사용자로 선점된 상태인지 검증하는 메서드
     */
    @Observed(name = "ticket.seat-cache.redis.validate-occupied-seat", contextualName = "ticket seat cache redis validate occupied seat")
    public LocalDateTime validateOccupiedSeat(Long eventId, String userId, String orderId, List<SeatInfo> seats) {
        LocalDateTime earliestExpiresAt = null;

        for (SeatInfo seat : seats) {
            String correctValue = buildSeatLockValue(userId, orderId);
            String redisKey = buildSeatRedisKey(eventId, seat.getZone(), seat.getRow(), seat.getCol());
            String redisKeyValue;
            Long ttlMillis;

            try {
                redisKeyValue = seatRedisTemplate.opsForValue().get(redisKey);
                ttlMillis = seatRedisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
            } catch (DataAccessException e) {
                log.error("[REDIS-ERROR] 좌석 선점 검증 Redis 조회 실패. operation=get, keyPrefix=event:{eventId}:seat, redisKey={}, eventId={}, userId={}, orderId={}",
                        redisKey, eventId, userId, orderId, e);
                throw new SeatOccupationFailedException("좌석 정보 검증 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
            }

            if (redisKeyValue == null || !redisKeyValue.equals(correctValue) || ttlMillis == null || ttlMillis <= 0) {
                log.error("[Redis 좌석 검증 오류 발생] eventId={}, zone={}, row={}, col={}, userId={}, orderId={}, redisKey={}",
                        eventId, seat.getZone(), seat.getRow(), seat.getCol(), userId, orderId, redisKey);
                log.error("[Redis 저장된 좌석 정보] value={}, ttlMillis={}", redisKeyValue, ttlMillis);

                throw new SeatOccupationFailedException("좌석 정보가 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
            }

            LocalDateTime expiresAt = LocalDateTime.now().plusNanos(ttlMillis * 1_000_000);
            if (earliestExpiresAt == null || expiresAt.isBefore(earliestExpiresAt)) {
                earliestExpiresAt = expiresAt;
            }
        }

        return earliestExpiresAt;
    }

    /** 결제 완료 좌석의 Redis 반영 작업을 DB 변경과 함께 저장한다. */
    @Observed(name = "ticket.seat-cache.redis.sync-reserved-after-commit", contextualName = "ticket seat cache redis sync reserved after commit")
    public void syncReservedSeatsAfterCommit(List<Seat> seats, String userId, String orderId) {
        seatCacheSyncOutboxService.enqueue(seats, "syncReservedSeatsAfterCommit", userId, orderId);
    }

    /** 관리자 보정으로 입금 대기가 된 좌석의 Redis 반영 작업을 저장한다. */
    public void syncLockedSeatsAfterCommit(List<Seat> seats, String userId, String orderId) {
        seatCacheSyncOutboxService.enqueue(seats, "syncLockedSeatsAfterCommit", userId, orderId);
    }

    /** 취소·환불·만료된 좌석의 Redis 반영 작업을 저장한다. */
    public void syncAvailableSeatsAfterCommit(List<Seat> seats, String userId, String orderId) {
        seatCacheSyncOutboxService.enqueue(seats, "syncAvailableSeatsAfterCommit", userId, orderId);
    }

    /** 선점을 시도한 좌석 중 이번 요청이 소유한 잠금만 원자적으로 해제한다. */
    private void rollbackSeats(List<String> redisKeys, String lockValue) {
        for (String redisKey : redisKeys) {
            try {
                seatRedisTemplate.execute(SeatOccupationScripts.ROLLBACK,
                        List.of(redisKey, redisKey + ":lock"), lockValue,
                        String.valueOf(SEAT_CACHE_TTL.toMillis()));
            } catch (DataAccessException e) {
                // 한 좌석의 정리 실패가 나머지 좌석 정리와 원래 예외 전달을 막지 않게 한다.
                log.error("[REDIS-ERROR] 좌석 선점 롤백 실패. redisKey={}", redisKey, e);
            }
        }
    }

    /** 이번 요청에서 선택한 좌석 수만 공연의 1인 최대 매수와 비교한다. */
    private void validateUserPurchaseLimit(SeatOccupyRequest request) {
        Event event = eventRepository.selectById(request.getEventId());
        int selectedSeatCount = request.getSeats().size();
        if (selectedSeatCount <= 0) {
            throw new IllegalArgumentException("선택한 좌석이 없습니다.");
        }
        if (event.getMaxTicketsPerPerson() < selectedSeatCount) {
            throw new TicketLimitExceededException(createPurchaseLimitExceededMessage(event, event.getMaxTicketsPerPerson()));
        }
    }
    private String createPurchaseLimitExceededMessage(Event event, int limitMax) {
        if (event.getTicketLimitScope() == PER_GROUP) {
            return "이 공연은 모든 공연을 포함해서 1인당 최대 " + limitMax + "매까지만 예매 가능합니다.";
        }

        return "이 공연은 1인당 최대 " + limitMax + "매까지만 예매 가능합니다.";
    }



    private void validateEventSaleStartAt(Event event) {
        if (event.getSaleStartAt() == null) {
            throw new IllegalStateException("판매 시작 시간이 설정되지 않아 좌석 캐시를 예열할 수 없습니다.");
        }
    }

    /**
     * 좌석 목록을 Redis 캐시에 적재하는 메서드
     * @param seats
     * @param mode
     * @return
     */
    private int warmUpSeatCache(List<Seat> seats, SeatCacheWarmUpMode mode, LocalDateTime saleStartAt) {
        if (seats.isEmpty()) {
            return 0;
        }

        if (mode == SeatCacheWarmUpMode.OVERWRITE) {
            if (!LocalDateTime.now().isBefore(saleStartAt)) {
                throw new IllegalStateException("판매 시작 이후에는 좌석 캐시를 덮어쓸 수 없습니다.");
            }

            Map<String, String> seatCacheMap = new HashMap<>();

            for (Seat seat : seats) {
                seatCacheMap.put(buildSeatRedisKey(seat), seat.getStatus().name());
            }

            if (!LocalDateTime.now().isBefore(saleStartAt)) {
                throw new IllegalStateException("판매 시작 이후에는 좌석 캐시를 덮어쓸 수 없습니다.");
            }

            seatRedisTemplate.opsForValue().multiSet(seatCacheMap);
            expireSeatKeys(new ArrayList<>(seatCacheMap.keySet()));
            return seatCacheMap.size();
        }

        if (mode == SeatCacheWarmUpMode.MISSING_ONLY) {
            int inserted = 0;

            // 판매 시작 시간 이후에는,
            // redis에  키가 존재하지 않고,
            // DB의 좌석 상태 값이 available인 좌석만 동기화
            if (!LocalDateTime.now().isBefore(saleStartAt)) {
                for (Seat seat : seats) {
                    if (seat.getStatus() != SeatStatus.AVAILABLE) {
                        continue;
                    }

                    String redisKey = buildSeatRedisKey(seat);
                    Long result = seatRedisTemplate.execute(
                            SAFE_WARM_UP_SCRIPT,
                            List.of(redisKey, redisKey + ":lock"),
                            String.valueOf(SEAT_CACHE_TTL.toMillis())
                    );

                    if (Long.valueOf(1L).equals(result)) {
                        inserted++;
                    }
                }

                return inserted;
            }

            // 판매 시작 시간 이전에는
            // redis에 키가 존재하지 않는 모든 좌석 값을 DB값으로 동기화
            else {
                for (Seat seat : seats) {
                    Boolean success = seatRedisTemplate.opsForValue()
                            .setIfAbsent(buildSeatRedisKey(seat), seat.getStatus().name(), SEAT_CACHE_TTL);
                    if (Boolean.TRUE.equals(success)) {
                        inserted++;
                    }
                }

                return inserted;
            }
        }

        return 0;
    }

    private static DefaultRedisScript<Long> safeWarmUpScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                if redis.call('exists', KEYS[1]) == 1 or redis.call('exists', KEYS[2]) == 1 then
                    return 0
                end
                redis.call('psetex', KEYS[1], ARGV[1], 'AVAILABLE')
                return 1
                """);
        script.setResultType(Long.class);
        return script;
    }

    private int normalizeInspectLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }

    private List<String> scanSeatKeys(String pattern, int limit) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(Math.min(limit * 2L, 1000L))
                .build();

        List<String> keys = seatRedisTemplate.execute((RedisCallback<List<String>>) connection -> {
            List<String> foundKeys = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext() && foundKeys.size() < limit * 2) {
                    foundKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return foundKeys;
        });

        return keys == null ? List.of() : keys;
    }

    private boolean matchesInspectMode(String key, SeatRedisInspectMode mode) {
        if (mode == SeatRedisInspectMode.LOCK) {
            return key.endsWith(":lock");
        }

        return !key.endsWith(":lock");
    }

    private String buildSeatInspectPattern(Long eventId, String zone, Integer row, Integer col, SeatRedisInspectMode mode) {
        StringBuilder pattern = new StringBuilder("event:")
                .append(eventId)
                .append(":seat:");
        boolean lockMode = mode == SeatRedisInspectMode.LOCK;

        if (zone == null || zone.isBlank()) {
            return pattern.append(lockMode ? "*:lock" : "*").toString();
        }

        pattern.append(zone.replace(" ", "_"));

        if (row == null) {
            return pattern.append(lockMode ? ":*:lock" : ":*").toString();
        }

        pattern.append(":").append(row);

        if (col == null) {
            return pattern.append(lockMode ? ":*:lock" : ":*").toString();
        }

        pattern.append(":").append(col);
        if (lockMode) {
            pattern.append(":lock");
        }

        return pattern.toString();
    }

    private SeatRedisEntryResponse toSeatRedisEntry(String redisKey) {
        String value = seatRedisTemplate.opsForValue().get(redisKey);
        Long ttlSeconds = seatRedisTemplate.getExpire(redisKey);
        DataType dataType = seatRedisTemplate.type(redisKey);
        String lockKey = redisKey + ":lock";
        String lockValue = seatRedisTemplate.opsForValue().get(lockKey);
        Long lockTtlSeconds = seatRedisTemplate.getExpire(lockKey);

        return SeatRedisEntryResponse.builder()
                .key(redisKey)
                .value(value)
                .ttlSeconds(ttlSeconds)
                .type(dataType == null ? null : dataType.name())
                .status(resolveSeatCacheStatus(value))
                .locked(lockValue != null || (value != null && value.startsWith("LOCKED:")))
                .lockKey(lockKey)
                .lockValue(lockValue)
                .lockTtlSeconds(lockTtlSeconds)
                .build();
    }

    private SeatRedisEntryResponse toSeatRedisLockEntry(String lockKey) {
        String lockValue = seatRedisTemplate.opsForValue().get(lockKey);
        Long ttlSeconds = seatRedisTemplate.getExpire(lockKey);
        DataType dataType = seatRedisTemplate.type(lockKey);
        String seatKey = lockKey.endsWith(":lock")
                ? lockKey.substring(0, lockKey.length() - ":lock".length())
                : lockKey;

        return SeatRedisEntryResponse.builder()
                .key(lockKey)
                .value(lockValue)
                .ttlSeconds(ttlSeconds)
                .type(dataType == null ? null : dataType.name())
                .status(lockValue == null ? "MISSING" : "LOCKED")
                .locked(lockValue != null)
                .lockKey(seatKey)
                .lockValue(seatRedisTemplate.opsForValue().get(seatKey))
                .lockTtlSeconds(seatRedisTemplate.getExpire(seatKey))
                .build();
    }

    private String resolveSeatCacheStatus(String value) {
        if (value == null) {
            return "MISSING";
        }
        if (value.startsWith("LOCKED:")) {
            return "LOCKED";
        }
        return value;
    }

    /**
     * 좌석 목록에 해당하는 Redis 캐시와 락 키를 삭제하는 메서드
     * @param seats
     * @return
     */
    private long deleteSeatCache(List<Seat> seats) {
        if (seats.isEmpty()) {
            return 0;
        }

        List<String> keys = new ArrayList<>(seats.size() * 2);
        for (Seat seat : seats) {
            String redisKey = buildSeatRedisKey(seat);
            keys.add(redisKey);
            keys.add(redisKey + ":lock");
        }

        Long deleted = seatRedisTemplate.delete(keys);
        return deleted != null ? deleted : 0;
    }

    /**
     * 좌석 Redis 캐시 키에 기본 만료 시간을 설정하는 메서드
     * @param keys
     */
    private void expireSeatKeys(List<String> keys) {
        seatRedisTemplate.executePipelined(new SessionCallback<Void>() {
            @Override
            public <K, V> Void execute(RedisOperations<K, V> operations) throws DataAccessException {
                for (String key : keys) {
                    operations.expire((K) key, SEAT_CACHE_TTL);
                }
                return null;
            }
        });
    }

    /**
     * 좌석 정보를 기준으로 Redis key를 생성하는 메서드
     * @param seat
     * @return
     */
    private String buildSeatRedisKey(Seat seat) {
        Long eventId = seat.getEvent().getEventId();
        return buildSeatRedisKey(eventId, seat.getZone(), seat.getSeatRow(), seat.getSeatCol());
    }

    /**
     * 좌석 식별 정보를 기준으로 Redis key를 생성하는 메서드
     * @param eventId
     * @param zone
     * @param row
     * @param col
     * @return
     */
    private String buildSeatRedisKey(Long eventId, String zone, int row, int col) {
        String sanitizedZone = zone.replace(" ", "_");
        return String.format("event:%d:seat:%s:%d:%d", eventId, sanitizedZone, row, col);
    }

    /**
     * 예매 흐름을 식별하기 위한 주문 ID를 생성하는 메서드
     * @return
     */
    private String generateOrderId() {
        String timestamp = LocalDateTime.now().format(ORDER_ID_FORMATTER);
        String randomValue = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "ORD-" + timestamp + "-" + randomValue;
    }

    /**
     * 좌석 선점 정보를 Redis에 저장하기 위한 값 생성 메서드
     * @param userId
     * @param orderId
     * @return
     */
    private String buildSeatLockValue(String userId, String orderId) {
        return "LOCKED:" + userId + ":" + orderId;
    }


}
