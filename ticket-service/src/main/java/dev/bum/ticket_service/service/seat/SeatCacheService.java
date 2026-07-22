package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException;
import dev.bum.ticket_service.exception.seat.SeatCacheNotFoundException;
import dev.bum.ticket_service.exception.seat.SeatOccupationFailedException;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheService {

    private static final Duration SEAT_CACHE_TTL = Duration.ofDays(7);
    private static final Duration SEAT_LOCK_TTL = Duration.ofMinutes(10);
    private static final DateTimeFormatter ORDER_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SeatRepository repository;
    private final StringRedisTemplate seatRedisTemplate;

    /**
     * 공연 단위 좌석 정보를 Redis에 적재하는 메서드
     * @param eventId
     * @param mode
     * @return
     */
    public String warmUpEventSeatsToCache(Long eventId, SeatCacheWarmUpMode mode) {
        log.info("[REDIS-WARM-UP] EventId : {}, Mode : {}", eventId, mode);
        List<Seat> seats = repository.selectByEventId(eventId);
        int updated = warmUpSeatCache(seats, mode);
        return String.format("이벤트 %d번 좌석 캐시 적재 완료 - 대상 %d개, 반영 %d개", eventId, seats.size(), updated);
    }

    /**
     * 구역 단위 좌석 정보를 Redis에 적재하는 메서드
     * @param areaId
     * @param mode
     * @return
     */
    public String warmUpAreaSeatsToCache(Long areaId, SeatCacheWarmUpMode mode) {
        log.info("[REDIS-WARM-UP] AreaId : {}, Mode : {}", areaId, mode);
        List<Seat> seats = repository.selectByAreaId(areaId);
        int updated = warmUpSeatCache(seats, mode);
        return String.format("구역 %d번 좌석 캐시 적재 완료 - 대상 %d개, 반영 %d개", areaId, seats.size(), updated);
    }

    /**
     * 공연 단위 좌석 캐시를 Redis에서 삭제하는 메서드
     * @param eventId
     * @return
     */
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
    public String deleteAreaSeatsFromCache(Long areaId) {
        log.info("[REDIS-DELETE] AreaId : {}", areaId);
        List<Seat> seats = repository.selectByAreaId(areaId);
        long deleted = deleteSeatCache(seats);
        return String.format("구역 %d번 좌석 캐시 삭제 완료 - 대상 %d개, 삭제 key %d개", areaId, seats.size(), deleted);
    }

    /**
     * 단일 좌석을 특정 사용자로 Redis 테스트 선점 처리하는 메서드
     * @param seatId
     * @param userId
     * @return
     */
    public String lockSeatCacheForUser(Long seatId, String userId) {
        log.info("[REDIS-TEST-LOCK] SeatId : {}, UserId : {}", seatId, userId);
        Seat seat = repository.selectById(seatId);
        String redisKey = buildSeatRedisKey(seat);
        String lockKey = redisKey + ":lock";
        String value = buildSeatLockValue(userId, generateOrderId());
        String currentStatus = seatRedisTemplate.opsForValue().get(redisKey);

        if (currentStatus == null && seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatAlreadyOccupiedException("이미 선점되었거나 예매 완료된 좌석입니다.");
        }

        if (currentStatus != null && !"AVAILABLE".equals(currentStatus)) {
            throw new SeatAlreadyOccupiedException("이미 선점되었거나 예매 완료된 좌석입니다.");
        }

        Boolean lockAcquired = seatRedisTemplate.opsForValue().setIfAbsent(lockKey, value, SEAT_LOCK_TTL);
        if (lockAcquired == null || !lockAcquired) {
            throw new SeatAlreadyOccupiedException("이미 다른 사용자가 선점 중인 좌석입니다.");
        }

        seatRedisTemplate.opsForValue().set(redisKey, value, SEAT_LOCK_TTL);

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
     * @param request
     */
    public SeatOccupyResponse occupySeat(SeatOccupyRequest request) {
        validateUserPurchaseLimit(request);

        long eventId = request.getEventId();
        String userId = request.getUserId();
        List<SeatInfo> seats = request.getSeats();
        String orderId = generateOrderId();
        LocalDateTime expiresAt = LocalDateTime.now().plus(SEAT_LOCK_TTL);

        List<String> acquiredLockKeys = new ArrayList<>();
        List<String> updatedRedisKeys = new ArrayList<>();

        try {
            for (SeatInfo seat : seats) {
                String redisKey = buildSeatRedisKey(eventId, seat.getZone(), seat.getRow(), seat.getCol());
                String currentStatus = seatRedisTemplate.opsForValue().get(redisKey);

                if (currentStatus == null) {
                    validateSeatAvailableFromDatabase(eventId, seat);
                    currentStatus = "AVAILABLE";
                }

                if (!"AVAILABLE".equals(currentStatus)) {
                    throw new SeatAlreadyOccupiedException("선택하신 좌석 중 이미 선택된 좌석이 포함되어 있습니다.");
                }
            }

            for (SeatInfo seat : seats) {
                String redisKey = buildSeatRedisKey(eventId, seat.getZone(), seat.getRow(), seat.getCol());
                String lockKey = redisKey + ":lock";
                String lockValue = buildSeatLockValue(userId, orderId);

                Boolean isLockAcquired = seatRedisTemplate.opsForValue()
                        .setIfAbsent(lockKey, lockValue, SEAT_LOCK_TTL);

                if (isLockAcquired == null || !isLockAcquired) {
                    throw new SeatAlreadyOccupiedException("선택하신 좌석 중 이미 선택된 좌석이 포함되어 있습니다.");
                }

                acquiredLockKeys.add(lockKey);
                seatRedisTemplate.opsForValue().set(redisKey, lockValue, SEAT_LOCK_TTL);
                updatedRedisKeys.add(redisKey);
            }

            return SeatOccupyResponse.builder()
                    .orderId(orderId)
                    .eventId(request.getEventId())
                    .userId(userId)
                    .seats(seats)
                    .expiresAt(expiresAt)
                    .build();
        } catch (SeatCacheNotFoundException | SeatAlreadyOccupiedException e) {
            rollbackSeats(acquiredLockKeys, updatedRedisKeys);
            throw e;
        } catch (Exception e) {
            rollbackSeats(acquiredLockKeys, updatedRedisKeys);
            log.error("[좌석 선점 실패] Redis 일괄 롤백 완료. 사유: {}", e.getMessage());
            throw new SeatOccupationFailedException("잠시 후 다시 시도해주세요.", e);
        }
    }

    /**
     * 예매 요청 좌석이 요청 사용자로 선점된 상태인지 검증하는 메서드
     * @param info
     */
    public void validateOccupiedSeat(InsertReservationRequest info) {
        long eventId = info.getEventId();
        String userId = info.getUserId();
        String orderId = info.getOrderId();
        List<SeatInfo> seats = info.getSeats();

        for (SeatInfo seat : seats) {
            String correctValue = buildSeatLockValue(userId, orderId);
            String redisKey = buildSeatRedisKey(eventId, seat.getZone(), seat.getRow(), seat.getCol());
            String redisKeyValue = seatRedisTemplate.opsForValue().get(redisKey);

            if (redisKeyValue == null || !redisKeyValue.equals(correctValue)) {
                log.error("[Redis 좌석 검증 오류 발생] : event = {}, zone = {}, row = {}, col = {}, userId = {}",
                        eventId, seat.getZone(), seat.getRow(), seat.getCol(), userId);
                log.error("[Redis 저장된 좌석 정보] : {}", redisKeyValue);

                throw new SeatOccupationFailedException("좌석 정보가 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
            }
        }
    }

    /**
     * 예매 확정 후 좌석 Redis 상태를 RESERVED로 동기화하는 메서드
     * @param seats
     */
    public void syncReservedSeatsAfterCommit(List<Seat> seats) {
        List<SeatCacheUpdate> updates = seats.stream()
                .map(seat -> new SeatCacheUpdate(
                        buildSeatRedisKey(seat),
                        SeatStatus.RESERVED.name(),
                        calculateSeatCacheTtl(seat)
                ))
                .collect(Collectors.toList());

        runAfterCommit(() -> {
            for (SeatCacheUpdate update : updates) {
                seatRedisTemplate.opsForValue().set(update.getRedisKey(), update.getValue(), update.getTtl());
                seatRedisTemplate.delete(update.getRedisKey() + ":lock");
            }
        });
    }

    /**
     * 예매 결제 대기 보정 후 좌석 Redis 상태를 LOCKED로 동기화하는 메서드
     * @param seats
     */
    public void syncLockedSeatsAfterCommit(List<Seat> seats) {
        List<SeatCacheUpdate> updates = seats.stream()
                .map(seat -> new SeatCacheUpdate(
                        buildSeatRedisKey(seat),
                        SeatStatus.LOCKED.name(),
                        calculateSeatCacheTtl(seat)
                ))
                .collect(Collectors.toList());

        runAfterCommit(() -> {
            for (SeatCacheUpdate update : updates) {
                seatRedisTemplate.opsForValue().set(update.getRedisKey(), update.getValue(), update.getTtl());
                seatRedisTemplate.delete(update.getRedisKey() + ":lock");
            }
        });
    }

    /**
     * 예매 취소 후 좌석 Redis 상태를 AVAILABLE로 동기화하는 메서드
     * @param seats
     */
    public void syncAvailableSeatsAfterCommit(List<Seat> seats) {
        List<SeatCacheUpdate> updates = seats.stream()
                .map(seat -> new SeatCacheUpdate(
                        buildSeatRedisKey(seat),
                        SeatStatus.AVAILABLE.name(),
                        calculateSeatCacheTtl(seat)
                ))
                .collect(Collectors.toList());

        runAfterCommit(() -> {
            for (SeatCacheUpdate update : updates) {
                seatRedisTemplate.opsForValue().set(update.getRedisKey(), update.getValue(), update.getTtl());
                seatRedisTemplate.delete(update.getRedisKey() + ":lock");
            }
        });
    }

    /**
     * Redis에 공연별 사용자 예매 매수를 반영하는 메서드
     * @param eventId
     * @param userId
     * @param purchaseCnt
     * @param type
     */
    public void updateUserPurchaseLimit(long eventId, String userId, int purchaseCnt, String type) {
        String purchaseKey = "user:purchase:limit:" + eventId + ":" + userId;

        int amount = type.equals("PLUS") ? purchaseCnt : -purchaseCnt;
        Long currentCount = seatRedisTemplate.opsForValue().increment(purchaseKey, amount);

        if (currentCount != null && currentCount < 0) {
            seatRedisTemplate.opsForValue().set(purchaseKey, "0");
            currentCount = 0L;
        }

        seatRedisTemplate.expire(purchaseKey, Duration.ofDays(30));
        log.info("[Redis 공연 매수 반영 (key : {}), (value : {})]", purchaseKey, currentCount);
    }

    /**
     * 다중 좌석 선점 중 오류가 발생했을 때 Redis 상태를 원복하는 메서드
     * @param lockKeys
     * @param redisKeys
     */
    private void rollbackSeats(List<String> lockKeys, List<String> redisKeys) {
        for (String redisKey : redisKeys) {
            seatRedisTemplate.opsForValue().set(redisKey, SeatStatus.AVAILABLE.name(), SEAT_CACHE_TTL);
        }

        if (!lockKeys.isEmpty()) {
            seatRedisTemplate.delete(lockKeys);
        }
    }

    /**
     * Redis에 예매 가능한 좌석 수가 남아있는지 검증하는 메서드
     * @param request
     */
    private void validateUserPurchaseLimit(SeatOccupyRequest request) {
        String purchaseKey = "user:purchase:limit:" + request.getEventId() + ":" + request.getUserId();
        String purchaseStr = seatRedisTemplate.opsForValue().get(purchaseKey);
        int purchaseCount = (purchaseStr == null) ? 0 : Integer.parseInt(purchaseStr);

        int limitMax = request.getMaxTicketsPerPerson();

        if (purchaseCount + request.getSeats().size() > limitMax) {
            throw new SeatOccupationFailedException("이 공연은 1인당 최대 " + limitMax + "매까지만 예매 가능합니다.");
        }
    }

    /**
     * Redis에 좌석 캐시가 없을 때 DB 기준으로 선점 가능 여부를 검증하는 메서드
     * @param eventId
     * @param seatInfo
     */
    private void validateSeatAvailableFromDatabase(Long eventId, SeatInfo seatInfo) {
        Seat seat = repository.selectById(seatInfo.getId());
        Long seatEventId = seat.getEvent() != null ? seat.getEvent().getEventId() : null;

        if (!eventId.equals(seatEventId)) {
            throw new SeatOccupationFailedException("좌석 정보가 공연 정보와 일치하지 않습니다.");
        }

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new SeatAlreadyOccupiedException("이미 선점되었거나 예매 완료된 좌석입니다.");
        }
    }

    /**
     * 좌석 목록을 Redis 캐시에 적재하는 메서드
     * @param seats
     * @param mode
     * @return
     */
    private int warmUpSeatCache(List<Seat> seats, SeatCacheWarmUpMode mode) {
        if (seats.isEmpty()) {
            return 0;
        }

        if (mode == SeatCacheWarmUpMode.OVERWRITE) {
            Map<String, String> seatCacheMap = new HashMap<>();
            for (Seat seat : seats) {
                seatCacheMap.put(buildSeatRedisKey(seat), SeatStatus.AVAILABLE.name());
            }
            seatRedisTemplate.opsForValue().multiSet(seatCacheMap);
            expireSeatKeys(new ArrayList<>(seatCacheMap.keySet()));
            return seatCacheMap.size();
        }

        int inserted = 0;
        for (Seat seat : seats) {
            Boolean success = seatRedisTemplate.opsForValue()
                    .setIfAbsent(buildSeatRedisKey(seat), SeatStatus.AVAILABLE.name(), SEAT_CACHE_TTL);
            if (Boolean.TRUE.equals(success)) {
                inserted++;
            }
        }
        return inserted;
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

    /**
     * 공연 종료 시점까지 좌석 캐시를 유지하기 위한 TTL 계산 메서드
     * @param seat
     * @return
     */
    private Duration calculateSeatCacheTtl(Seat seat) {
        if (seat.getEvent() == null || seat.getEvent().getEventDateTime() == null) {
            return SEAT_CACHE_TTL;
        }

        int runningMinutes = seat.getEvent().getRunningMinutes() != null
                ? seat.getEvent().getRunningMinutes()
                : 0;
        LocalDateTime eventEndAt = seat.getEvent().getEventDateTime().plusMinutes(runningMinutes);
        Duration ttl = Duration.between(LocalDateTime.now(), eventEndAt);

        return ttl.isPositive() ? ttl : Duration.ofMinutes(1);
    }

    /**
     * DB 트랜잭션 커밋 이후 Redis 상태를 동기화하기 위한 후처리 등록 메서드
     * DB 처리 중 오류가 생기면 호출되지 않는다. (즉, 트랜잭션 롤백 시 실행되지 않음)
     * @param runnable
     */
    private void runAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 좌석 DB 트랜잭션 커밋 이후 Redis 동기화 작업을 실행한다.
             */
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    /**
     * 좌석 Redis 동기화 정보를 임시 보관하는 내부 클래스
     */
    private static class SeatCacheUpdate {
        private final String redisKey;
        private final String value;
        private final Duration ttl;

        /**
         * Redis key, 저장 값, TTL을 하나의 동기화 작업 단위로 묶는다.
         */
        private SeatCacheUpdate(String redisKey, String value, Duration ttl) {
            this.redisKey = redisKey;
            this.value = value;
            this.ttl = ttl;
        }

        /**
         * 동기화할 Redis key를 반환한다.
         */
        private String getRedisKey() {
            return redisKey;
        }

        /**
         * Redis에 저장할 좌석 상태 값을 반환한다.
         */
        private String getValue() {
            return value;
        }

        /**
         * Redis key에 적용할 만료 시간을 반환한다.
         */
        private Duration getTtl() {
            return ttl;
        }
    }
}
