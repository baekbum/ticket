package dev.bum.ticket_service.service.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository repository;
    private final SeatCacheService seatCacheService;

    /**
     * 좌석 정보 등록 메서드
     * @param info
     */
    @AuditLog(action = "SEAT_CREATE", targetType = "SEAT")
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

    /**
     * 공연 ID 기준 좌석 개수 조회 메서드
     * @param eventId
     * @return
     */
    public long countByEventId(Long eventId) {
        log.info("[COUNT] EventId : {}", eventId);
        return repository.countByEventId(eventId);
    }

    /**
     * ID를 통해 좌석 정보 조회 메서드
     * @param id
     * @return
     */
    @Transactional(readOnly = true)
    public SeatResponse selectById(Long id) {
        log.info("[SELECT] SeatId : {}", id);
        return repository.selectById(id).toDto();
    }

    /**
     * 조건을 통해 좌석 정보 조회 메서드
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
     * 좌석 정보 수정 메서드
     * @param info
     */
    @AuditLog(action = "SEAT_UPDATE", targetType = "SEAT")
    public void update(UpdateSeatRequest info) {
        log.info("[UPDATE] {}", info.toString());
        repository.update(info);
    }

    /**
     * 좌석 정보 삭제 메서드
     * @param id
     */
    @AuditLog(action = "SEAT_DELETE", targetType = "SEAT")
    public void delete(Long id) {
        log.info("[DELETE] SeatId : {}", id);
        repository.delete(id);
    }

    /**
     * 선택한 좌석 일괄 삭제 메서드
     * @param info
     */
    @AuditLog(action = "SEAT_DELETE_BULK", targetType = "SEAT")
    public void deleteBySeatIdList(DeleteSeatRequest info) {
        if (!info.getSeatIdList().isEmpty()) {
            log.info("[DELETE] {}", info);
            repository.deleteByIdList(info.getSeatIdList());
        }
    }

    /**
     * 구역 기준 좌석 삭제 메서드
     * @param areaId
     */
    @AuditLog(action = "SEAT_DELETE_BY_AREA", targetType = "SEAT")
    public void deleteByAreaId(Long areaId) {
        log.info("[DELETE] AreaId : {}", areaId);
        repository.deleteByAreaId(areaId);
    }

    /**
     * 공연 단위 좌석 정보를 Redis에 적재하는 메서드
     * @param eventId
     * @param mode
     * @return
     */
    public String warmUpEventSeatsToCache(Long eventId, SeatCacheWarmUpMode mode) {
        return seatCacheService.warmUpEventSeatsToCache(eventId, mode);
    }

    /**
     * 구역 단위 좌석 정보를 Redis에 적재하는 메서드
     * @param areaId
     * @param mode
     * @return
     */
    public String warmUpAreaSeatsToCache(Long areaId, SeatCacheWarmUpMode mode) {
        return seatCacheService.warmUpAreaSeatsToCache(areaId, mode);
    }

    /**
     * 공연 단위 좌석 캐시를 Redis에서 삭제하는 메서드
     * @param eventId
     * @return
     */
    public String deleteEventSeatsFromCache(Long eventId) {
        return seatCacheService.deleteEventSeatsFromCache(eventId);
    }

    /**
     * 구역 단위 좌석 캐시를 Redis에서 삭제하는 메서드
     * @param areaId
     * @return
     */
    public String deleteAreaSeatsFromCache(Long areaId) {
        return seatCacheService.deleteAreaSeatsFromCache(areaId);
    }

    @Transactional(readOnly = true)
    public SeatRedisInspectResponse inspectEventSeatCache(Long eventId, int limit) {
        return seatCacheService.inspectEventSeatCache(eventId, limit);
    }

    @Transactional(readOnly = true)
    public SeatRedisInspectResponse inspectAreaSeatCache(Long areaId, int limit) {
        return seatCacheService.inspectAreaSeatCache(areaId, limit);
    }

    @Transactional(readOnly = true)
    public SeatRedisInspectResponse inspectSeatCache(Long seatId) {
        return seatCacheService.inspectSeatCache(seatId);
    }

    /**
     * 단일 좌석을 특정 사용자로 Redis 테스트 선점 처리하는 메서드
     * @param seatId
     * @param userId
     * @return
     */
    @AuditLog(action = "SEAT_LOCK", targetType = "SEAT")
    public String lockSeatCacheForUser(Long seatId, String userId) {
        return seatCacheService.lockSeatCacheForUser(seatId, userId);
    }

    /**
     * 단일 좌석 Redis 테스트 선점을 해제하는 메서드
     * @param seatId
     * @return
     */
    @AuditLog(action = "SEAT_UNLOCK", targetType = "SEAT")
    public String unlockSeatCache(Long seatId) {
        return seatCacheService.unlockSeatCache(seatId);
    }

    /**
     * Redis를 이용한 다중 좌석 선점 메서드
     * @param request
     */
    @AuditLog(action = "SEAT_OCCUPY", targetType = "SEAT")
    public SeatOccupyResponse occupySeat(SeatOccupyRequest request) {
        return seatCacheService.occupySeat(request);
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
}
