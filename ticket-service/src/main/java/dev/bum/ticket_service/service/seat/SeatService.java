package dev.bum.ticket_service.service.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.seat.Seat;
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
    private final SeatCacheService seatCacheService;

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
