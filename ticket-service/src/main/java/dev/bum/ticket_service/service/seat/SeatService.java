package dev.bum.ticket_service.service.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.exception.seat.SeatAlreadyOccupiedException;
import dev.bum.ticket_service.exception.seat.SeatCacheNotFoundException;
import dev.bum.ticket_service.exception.seat.SeatOccupationFailedException;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository repository;
    private final StringRedisTemplate seatRedisTemplate;

    /**
     * 좌석 정보 등록
     * @param info
     * @return
     */
    public void insert(InsertSeatRequest info) {
        if (!info.getInsertSeatAreaConfigs().isEmpty()) {
            for (InsertSeatAreaConfig config : info.getInsertSeatAreaConfigs()) {
                log.info("[INSERT] EventId : {}, Zone : {}, Rows : {}, Cols : {}, Price : {}, Grade : {}",
                        info.getEventId(),
                        config.getZone(),
                        config.getRows(),
                        config.getCols(),
                        config.getPrice(),
                        config.getGrade()
                );
            }
        }

        repository.insert(info);
    }

    public long countByEventId(Long eventId) {
        log.info("[COUNT] EventId : {}", eventId);
        return repository.countByEventId(eventId);
    }

    /**
     * Id값을 통해 좌석 정보 조회
     * @param id
     * @return
     */
    @Transactional(readOnly = true)
    public SeatResponse selectById(Long id) {
        log.info("[SELECT] SeatId : {}", id);
        return repository.selectById(id).toDto();
    }

    /**
     * 조건을 통해 좌석 정보 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<SeatResponse> selectByCond(SeatCondRequest cond) {
        log.info("[SELECT] cond : {}", cond.toString());
        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));

        Page<SeatResponse> seatPage = repository.selectByCond(cond, pageable).map(Seat::toDto);

        return CustomPageResponse.of(
                seatPage.getContent(),
                seatPage.getSize(),
                seatPage.getNumber(),
                seatPage.getTotalElements(),
                seatPage.getTotalPages()
        );
    }

    /**
     * 좌석 정보 수정
     * @param info
     * @return
     */
    public void update(UpdateSeatRequest info) {
        log.info("[UPDATE] {}", info.toString());
        repository.update(info);
    }

    /**
     * 좌석 정보 삭제
     * @param id
     * @return
     */
    public void delete(Long id) {
        log.info("[DELETE] SeatId : {}", id);
        repository.delete(id);
    }

    /**
     * 선택된 좌석 일괄 삭제
     * @param info
     */
    public void deleteBySeatIdList(DeleteSeatRequest info) {
        if (!info.getSeatIdList().isEmpty()) {
            log.info("[DELETE] {}", info);
            repository.deleteByIdList(info.getSeatIdList());
        }
    }

    public void deleteByAreaId(Long areaId) {
        log.info("[DELETE] AreaId : {}", areaId);
        repository.deleteByAreaId(areaId);
    }

    /**
     * 검색 조건에서 sort 옵션을 처리하기 위한 메서드
     * @param sorts
     * @return
     */
    private Sort makeSortInfo(List<String> sorts) {
        Sort sort = Sort.unsorted();
        if (sorts != null && !sorts.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();

            for (String infoStr : sorts) {
                String[] infos = infoStr.split("-");

                if (infos.length == 2) {
                    String field = infos[0];
                    String direction = infos[1];
                    orders.add(new Sort.Order(Sort.Direction.fromString(direction), field));
                }
            }
            sort = Sort.by(orders);
        }

        return sort;
    }

    public String warmUpEventSeatsToCache(Long eventId, SeatCacheWarmUpMode mode) {
        log.info("[REDIS-WARM-UP] EventId : {}, Mode : {}", eventId, mode);
        List<Seat> seats = repository.selectByEventId(eventId);
        int updated = warmUpSeatCache(seats, mode);
        return String.format("이벤트 %d번 좌석 캐시 적재 완료 - 대상 %d석, 반영 %d석", eventId, seats.size(), updated);
    }

    public String warmUpAreaSeatsToCache(Long areaId, SeatCacheWarmUpMode mode) {
        log.info("[REDIS-WARM-UP] AreaId : {}, Mode : {}", areaId, mode);
        List<Seat> seats = repository.selectByAreaId(areaId);
        int updated = warmUpSeatCache(seats, mode);
        return String.format("구역 %d번 좌석 캐시 적재 완료 - 대상 %d석, 반영 %d석", areaId, seats.size(), updated);
    }

    public String deleteEventSeatsFromCache(Long eventId) {
        log.info("[REDIS-DELETE] EventId : {}", eventId);
        List<Seat> seats = repository.selectByEventId(eventId);
        long deleted = deleteSeatCache(seats);
        return String.format("이벤트 %d번 좌석 캐시 삭제 완료 - 대상 %d석, 삭제 key %d개", eventId, seats.size(), deleted);
    }

    public String deleteAreaSeatsFromCache(Long areaId) {
        log.info("[REDIS-DELETE] AreaId : {}", areaId);
        List<Seat> seats = repository.selectByAreaId(areaId);
        long deleted = deleteSeatCache(seats);
        return String.format("구역 %d번 좌석 캐시 삭제 완료 - 대상 %d석, 삭제 key %d개", areaId, seats.size(), deleted);
    }

    public String lockSeatCacheForUser(Long seatId, String userId) {
        log.info("[REDIS-TEST-LOCK] SeatId : {}, UserId : {}", seatId, userId);
        Seat seat = repository.selectById(seatId);
        String redisKey = buildSeatRedisKey(seat);
        String lockKey = redisKey + ":lock";
        String value = "LOCKED:" + userId;
        String currentStatus = seatRedisTemplate.opsForValue().get(redisKey);

        if (currentStatus == null) {
            throw new SeatCacheNotFoundException();
        }

        if (!"AVAILABLE".equals(currentStatus)) {
            throw new SeatAlreadyOccupiedException("이미 선점되었거나 예매 완료된 좌석입니다.");
        }

        Boolean lockAcquired = seatRedisTemplate.opsForValue().setIfAbsent(lockKey, value, Duration.ofMinutes(10));
        if (lockAcquired == null || !lockAcquired) {
            throw new SeatAlreadyOccupiedException("이미 다른 사용자가 선점 중인 좌석입니다.");
        }

        seatRedisTemplate.opsForValue().set(redisKey, value, Duration.ofDays(30));

        return String.format("좌석 %d번을 %s 사용자로 Redis 테스트 선점 처리했습니다.", seatId, userId);
    }

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

        seatRedisTemplate.opsForValue().set(redisKey, "AVAILABLE", Duration.ofDays(7));
        seatRedisTemplate.delete(lockKey);

        return String.format("좌석 %d번의 Redis 테스트 선점을 취소했습니다.", seatId);
    }

    private int warmUpSeatCache(List<Seat> seats, SeatCacheWarmUpMode mode) {
        if (seats.isEmpty()) {
            return 0;
        }

        if (mode == SeatCacheWarmUpMode.OVERWRITE) {
            Map<String, String> seatCacheMap = new HashMap<>();
            for (Seat seat : seats) {
                seatCacheMap.put(buildSeatRedisKey(seat), "AVAILABLE");
            }
            seatRedisTemplate.opsForValue().multiSet(seatCacheMap);
            expireSeatKeys(new ArrayList<>(seatCacheMap.keySet()));
            return seatCacheMap.size();
        }

        int inserted = 0;
        for (Seat seat : seats) {
            Boolean success = seatRedisTemplate.opsForValue()
                    .setIfAbsent(buildSeatRedisKey(seat), "AVAILABLE", Duration.ofDays(7));
            if (Boolean.TRUE.equals(success)) {
                inserted++;
            }
        }
        return inserted;
    }

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

    private void expireSeatKeys(List<String> keys) {
        seatRedisTemplate.executePipelined(new SessionCallback<Void>() {
            @Override
            public <K, V> Void execute(RedisOperations<K, V> operations) throws DataAccessException {
                for (String key : keys) {
                    operations.expire((K) key, Duration.ofDays(7));
                }
                return null;
            }
        });
    }

    private String buildSeatRedisKey(Seat seat) {
        String sanitizedZone = seat.getZone().replace(" ", "_");
        Long eventId = seat.getEvent().getEventId();
        return String.format("event:%d:seat:%s:%d:%d", eventId, sanitizedZone, seat.getSeatRow(), seat.getSeatCol());
    }

    /**
     * redis에 예매 가능한 좌석이 남아있는지 확인하는 메서드
     * @param request
     */
    private void validateUserPurchaseLimit(SeatOccupyRequest request) {
        String purchaseKey = "user:purchase:limit:" + request.getEventId() + ":" + request.getUserId();
        String purchaseStr = seatRedisTemplate.opsForValue().get(purchaseKey);
        int purchaseCount = (purchaseStr == null) ? 0 : Integer.parseInt(purchaseStr);

        int limitMax = request.getMaxTicketsPerPerson(); // 공연 제한 매수

        if (purchaseCount + request.getSeats().size() > limitMax) {
            throw new SeatOccupationFailedException("이 공연은 1인당 최대 " + limitMax + "매까지만 예매 가능합니다.");
        }
    }

    /**
     * redis에 해당 공연에 대해 유저가 몇 장을 예매했는지 기록하는 메서드
     * @param eventId
     * @param userId
     * @param purchaseCnt
     * @param type
     */
    public void updateUserPurchaseLimit(long eventId, String userId, int purchaseCnt, String type) {
        String purchaseKey = "user:purchase:limit:" + eventId + ":" + userId;

        int amount = type.equals("PLUS") ? purchaseCnt : -purchaseCnt;

        Long currentCount = seatRedisTemplate.opsForValue().increment(purchaseKey, amount);

        // 만약 최종 값이 0보다 작아지면 0으로 보정
        if (currentCount != null && currentCount < 0) {
            seatRedisTemplate.opsForValue().set(purchaseKey, "0");
            currentCount = 0L;
        }

        // 만료 시간 30일 갱신
        seatRedisTemplate.expire(purchaseKey, Duration.ofDays(30));

        log.info("[REDIS에 공연 매수 반영 (key : {}), (value : {})]", purchaseKey, currentCount);
    }

    /**
     * Redis를 이용한 동시성 제어 다중 좌석 선점 로직
     * @param request
     */
    public void occupySeat(SeatOccupyRequest request) {
        // redis에 좌석을 선점하기 전에 예매 가능 매수가 남아있는지 확인하는 과정
        validateUserPurchaseLimit(request);

        long eventId = request.getEventId();
        String userId = request.getUserId();
        List<SeatInfo> seats = request.getSeats();

        List<String> acquiredLockKeys = new ArrayList<>();
        List<String> updatedRedisKeys = new ArrayList<>();

        try {
            // 1. 선점하려는 모든 좌석의 상태가 AVAILABLE 인지 먼저 전체 검증
            for (SeatInfo seat : seats) {
                String redisKey = String.format("event:%d:seat:%s:%d:%d", eventId, seat.getZone(), seat.getRow(), seat.getCol());

                String currentStatus = seatRedisTemplate.opsForValue().get(redisKey);

                if (currentStatus == null) {
                    throw new SeatCacheNotFoundException();
                }

                if (!currentStatus.equals("AVAILABLE")) {
                    throw new SeatAlreadyOccupiedException("선택하신 좌석 중 이미 선택된 좌석이 포함되어 있습니다.");
                }
            }

            // 2. 하나씩 락을 획득.
            for (SeatInfo seat : seats) {
                String redisKey = String.format("event:%d:seat:%s:%d:%d", eventId, seat.getZone(), seat.getRow(), seat.getCol());
                String lockKey = redisKey + ":lock";

                // 락 획득 시도 (SETNX)
                Boolean isLockAcquired = seatRedisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "LOCKED:" + userId, Duration.ofMinutes(10));

                // 0.0001초 차이로 다른 사람에게 밀렸거나 null인 경우
                if (isLockAcquired == null || !isLockAcquired) {
                    throw new SeatAlreadyOccupiedException("선택하신 좌석 중 이미 선택된 좌석이 포함되어 있습니다.");
                }

                // 롤백 추적 리스트에 락 키 기록
                acquiredLockKeys.add(lockKey);

                // 메인 좌석 상태를 LOCKED로 변경
                seatRedisTemplate.opsForValue().set(redisKey, "LOCKED:" + userId, Duration.ofDays(30));

                // 롤백 추적 리스트에 메인 키 기록
                updatedRedisKeys.add(redisKey);
            }
        } catch (SeatCacheNotFoundException | SeatAlreadyOccupiedException e) {
            // 비즈니스 예외(이선좌 등) 발생 시 그동안 잠근 자리가 있다면 전부 롤백
            rollbackSeats(acquiredLockKeys, updatedRedisKeys);
            throw e;
        } catch (Exception e) {
            rollbackSeats(acquiredLockKeys, updatedRedisKeys);
            log.error("[좌석 선점 실패] 레디스 일괄 롤백 완료. 사유: {}", e.getMessage());
            throw new SeatOccupationFailedException("잠시후 다시 시도해주세요.", e);
        }
    }

    /**
     * 해당 좌석의 정보가 예매한 유저와 일치하는지 확인하는 메서드
     * @param info
     */
    public void validateOccupiedSeat(InsertReservationRequest info) {
        long eventId = info.getEventId();
        String userId = info.getUserId();
        List<SeatInfo> seats = info.getSeats();

        // 현재 좌석이 해당 userId로 lock 상태인지 확인.
        for (SeatInfo seat : seats) {
            String correctValue = "LOCKED:" + userId;

            // 레디스에 현재 좌석의 값이 해당 유저의 ID가 맞는지 확인.
            String redisKey = String.format("event:%d:seat:%s:%d:%d", eventId, seat.getZone(), seat.getRow(), seat.getCol());
            String redisKeyValue = seatRedisTemplate.opsForValue().get(redisKey);

            if (redisKeyValue == null || !redisKeyValue.equals(correctValue)) {
                log.error("[Redis 좌석 검증 오류 발생] : event = {}, zone = {}, row = {}, col = {}, userId = {}", eventId, seat.getZone(), seat.getRow(), seat.getCol(), userId);
                log.error("[Redis 저장된 좌석 정보] : {}", redisKeyValue);

                throw new SeatOccupationFailedException("좌석 정보가 올바르지 않습니다. 잠시후 다시 시도해주세요.");
            }
        }
    }

    /**
     * 다중 좌석 처리 중 오류 발생 시, 여태까지 잠갔던 모든 레디스 데이터를 원상복구하는 헬퍼 메서드
     */
    private void rollbackSeats(List<String> lockKeys, List<String> redisKeys) {
        // 1. 상태 전광판 다시 AVAILABLE로 원복
        for (String redisKey : redisKeys) {
            seatRedisTemplate.opsForValue().set(redisKey, "AVAILABLE", Duration.ofDays(7));
        }
        // 2. 임시 락 키(:lock) 한방에 삭제
        if (!lockKeys.isEmpty()) {
            seatRedisTemplate.delete(lockKeys);
        }
    }

    /**
     * 티켓 취소 시 redis에 좌석 상태 정보 반영을 하기 위한 메서드
     * @param eventId
     * @param zone
     * @param row
     * @param col
     */
    public void releaseSeat(Long eventId, String zone, int row, int col) {
        String redisKey = String.format("event:%d:seat:%s:%d:%d", eventId, zone, row, col);
        String lockKey = redisKey + ":lock";

        seatRedisTemplate.opsForValue().set(redisKey, "AVAILABLE", Duration.ofDays(7));
        seatRedisTemplate.delete(lockKey);

        log.info("[좌석 취소 반영 완료] - Key: {}", redisKey);
    }
}
