package dev.bum.ticket_service.service.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatInsertMode;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.enums.SeatRedisInspectMode;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.exception.area.AreaNotExistException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.exception.seat.SeatLayoutAlreadyExistsException;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import io.micrometer.observation.annotation.Observed;
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
    private final EventRepository eventRepository;
    private final AreaJpaRepository areaJpaRepository;
    private final SeatCacheService seatCacheService;

    /**
     * 좌석 정보 등록 메서드
     * @param info
     */
    @AuditLog(action = "SEAT_CREATE", targetType = "SEAT")
    public void insert(InsertSeatRequest info) {
        SeatInsertTarget target = resolveSingleTarget(info);
        SeatInsertMode mode = resolveMode(info.getMode());

        validateExistingSeats(List.of(target), mode);
        deleteExistingSeatsIfNeeded(List.of(target), mode);
        insertSeats(info, mode);
    }

    /**
     * 같은 이벤트 그룹의 모든 회차에 동일한 좌석 구조를 생성한다.
     */
    @AuditLog(action = "SEAT_CREATE_GROUP", targetType = "SEAT")
    public void insertByEventGroupCode(InsertSeatGroupRequest info) {
        SeatInsertMode mode = resolveMode(info.getMode());
        List<Event> events = eventRepository.selectByEventGroupCode(info.getEventGroupCode());
        List<SeatInsertTarget> targets = events.stream()
                .map(event -> resolveGroupTarget(event, info.getAreaLayoutKey()))
                .toList();

        validateExistingSeats(targets, mode);
        deleteExistingSeatsIfNeeded(targets, mode);

        for (SeatInsertTarget target : targets) {
            InsertSeatRequest request = InsertSeatRequest.builder()
                    .eventId(target.eventId)
                    .areaId(target.areaId)
                    .mode(mode)
                    .insertSeatAreaConfigs(info.getInsertSeatAreaConfigs())
                    .build();

            insertSeats(request, mode);
        }
    }

    private SeatInsertTarget resolveSingleTarget(InsertSeatRequest info) {
        Area area = info.getAreaId() != null
                ? areaJpaRepository.findById(info.getAreaId())
                .orElseThrow(() -> new AreaNotExistException("해당 구역 정보는 존재하지 않습니다."))
                : null;

        return SeatInsertTarget.builder()
                .eventId(info.getEventId())
                .areaId(info.getAreaId())
                .areaName(area != null ? area.getAreaName() : "전체")
                .build();
    }

    private SeatInsertTarget resolveGroupTarget(Event event, String areaLayoutKey) {
        Area area = areaJpaRepository.findByEvent_EventIdAndLayoutKey(event.getEventId(), areaLayoutKey)
                .orElseThrow(() -> new AreaNotExistException("같은 구역 배치 키를 가진 구역 정보가 존재하지 않습니다."));

        return SeatInsertTarget.builder()
                .eventId(event.getEventId())
                .areaId(area.getAreaId())
                .areaName(area.getAreaName())
                .build();
    }

    private void validateExistingSeats(List<SeatInsertTarget> targets, SeatInsertMode mode) {
        if (mode != SeatInsertMode.FAIL_IF_EXISTS) return;

        targets.stream()
                .filter(target -> target.areaId != null)
                .filter(target -> seatCountByAreaId(target.areaId) > 0)
                .findFirst()
                .ifPresent(target -> {
                    throw new SeatLayoutAlreadyExistsException(
                            target.eventId + "번 이벤트의 [" + target.areaName + "] 구역에는 이미 좌석이 존재합니다."
                    );
                });
    }

    private void deleteExistingSeatsIfNeeded(List<SeatInsertTarget> targets, SeatInsertMode mode) {
        if (mode != SeatInsertMode.REPLACE) return;

        targets.stream()
                .filter(target -> target.areaId != null)
                .forEach(target -> repository.deleteByAreaId(target.areaId));
    }

    private long seatCountByAreaId(Long areaId) {
        return repository.countByAreaId(areaId);
    }

    private void insertSeats(InsertSeatRequest info, SeatInsertMode mode) {
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

        if (mode == SeatInsertMode.APPEND || info.getAreaId() != null) {
            repository.insertAppend(info);
        } else {
            repository.insert(info);
        }
    }

    private SeatInsertMode resolveMode(SeatInsertMode mode) {
        return mode != null ? mode : SeatInsertMode.FAIL_IF_EXISTS;
    }

    @lombok.Builder
    private static class SeatInsertTarget {
        private Long eventId;
        private Long areaId;
        private String areaName;
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

    @Transactional(readOnly = true)
    public CustomPageResponse<SeatResponse> selectByCondWithCacheStatus(SeatCondRequest cond) {
        log.info("[SELECT-TEST] cond : {}", cond.toString());
        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));

        Page<SeatResponse> seatPage = repository.selectByCond(cond, pageable)
                .map(Seat::toDto)
                .map(seatCacheService::applyCachedStatus);

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
    @Observed(name = "ticket.seat-cache.warm-up-event", contextualName = "ticket seat cache warm up event")
    public String warmUpEventSeatsToCache(Long eventId, SeatCacheWarmUpMode mode) {
        return seatCacheService.warmUpEventSeatsToCache(eventId, mode);
    }

    /**
     * 구역 단위 좌석 정보를 Redis에 적재하는 메서드
     * @param areaId
     * @param mode
     * @return
     */
    @Observed(name = "ticket.seat-cache.warm-up-area", contextualName = "ticket seat cache warm up area")
    public String warmUpAreaSeatsToCache(Long areaId, SeatCacheWarmUpMode mode) {
        return seatCacheService.warmUpAreaSeatsToCache(areaId, mode);
    }

    /**
     * 공연 단위 좌석 캐시를 Redis에서 삭제하는 메서드
     * @param eventId
     * @return
     */
    @Observed(name = "ticket.seat-cache.delete-event", contextualName = "ticket seat cache delete event")
    public String deleteEventSeatsFromCache(Long eventId) {
        return seatCacheService.deleteEventSeatsFromCache(eventId);
    }

    /**
     * 구역 단위 좌석 캐시를 Redis에서 삭제하는 메서드
     * @param areaId
     * @return
     */
    @Observed(name = "ticket.seat-cache.delete-area", contextualName = "ticket seat cache delete area")
    public String deleteAreaSeatsFromCache(Long areaId) {
        return seatCacheService.deleteAreaSeatsFromCache(areaId);
    }

    @Transactional(readOnly = true)
    public SeatRedisInspectResponse inspectEventSeatCache(Long eventId, String zone, Integer row, Integer col, int limit, SeatRedisInspectMode mode) {
        return seatCacheService.inspectEventSeatCache(eventId, zone, row, col, limit, mode);
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

    @AuditLog(action = "SEAT_UNLOCK_EVENT", targetType = "SEAT")
    public String unlockEventSeatCache(Long eventId) {
        return seatCacheService.unlockEventSeatCache(eventId);
    }

    /**
     * Redis를 이용한 다중 좌석 선점 메서드
     * @param request
     */
    @AuditLog(action = "SEAT_OCCUPY", targetType = "SEAT")
    @Observed(name = "ticket.seat.occupy", contextualName = "ticket seat occupy")
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
