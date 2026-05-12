package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.dto.SeatDto;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
import dev.bum.ticket_service.vo.seat.UpdateSeatInfo;
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

    /**
     * 좌석 정보 등록
     * @param info
     * @return
     */
    public void insert(InsertSeatInfo info) {
        repository.insert(info);
    }

    public long countByEventId(Long eventId) {
        return repository.countByEventId(eventId);
    }

    /**
     * Id값을 통해 좌석 정보 조회
     * @param id
     * @return
     */
    @Transactional(readOnly = true)
    public SeatDto selectById(Long id) {
        return new SeatDto(repository.selectById(id));
    }

    /**
     * 조건을 통해 좌석 정보 조회
     * @param cond
     * @return
     */
    @Transactional(readOnly = true)
    public Page<SeatDto> selectByCond(SeatCond cond) {

        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));

        Page<Seat> seats = repository.selectByCond(cond, pageable);

        return seats.map(SeatDto::new);
    }

    /**
     * 좌석 정보 수정
     * @param info
     * @return
     */
    public void update(UpdateSeatInfo info) {
        repository.update(info);
    }

    /**
     * 좌석 정보 삭제
     * @param id
     * @return
     */
    public void delete(Long id) {
        repository.delete(id);
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
