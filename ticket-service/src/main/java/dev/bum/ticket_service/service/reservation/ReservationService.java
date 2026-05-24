package dev.bum.ticket_service.service.reservation;

import dev.bum.ticket_service.dto.ReservationDto;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.ReservationRepository;
import dev.bum.ticket_service.vo.reservation.CancelReservationInfo;
import dev.bum.ticket_service.vo.reservation.InsertReservationInfo;
import dev.bum.ticket_service.vo.reservation.IsReservableInfo;
import dev.bum.ticket_service.vo.reservation.ReservationCond;
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
public class ReservationService {

    private final ReservationRepository repository;

    public void insert(InsertReservationInfo info) {
        repository.insert(info);
    }

    public ReservationDto selectById(long id) {
        return new ReservationDto(repository.selectById(id));
    }

    public Page<ReservationDto> selectByCond(ReservationCond cond) {
        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));

        Page<Reservation> reservations = repository.selectByCond(cond, pageable);

        return reservations.map(ReservationDto::new);
    }

    public void cancel(long id, CancelReservationInfo info) {
        repository.cancel(id, info);
    }

    public void isReservable(IsReservableInfo info) {
        repository.isReservable(info.getUserId(), info.getEventId(), info.getSelectedSeatCnt());
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
