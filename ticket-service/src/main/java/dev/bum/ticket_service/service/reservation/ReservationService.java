package dev.bum.ticket_service.service.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.*;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.ReservationRepository;
import dev.bum.ticket_service.kafka.reservation.ReservationProducer;
import dev.bum.ticket_service.service.seat.SeatService;
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
    private final SeatService seatService;
    private final ReservationProducer reservationProducer;

    public void insert(InsertReservationRequest info) {
        // 1. Redis에 좌석 정보가 일치하는지 검증 및 좌석 정보 업데이트
        seatService.validateOccupiedSeat(info);
        log.info("[좌석 선점 완료 (eventId : {}), (userId : {})]", info.getEventId(), info.getUserId());

        // 2. 이후 카프카를 통해 업데이트로 변경 예정
        reservationProducer.send(info);
    }

    // 카프카 컨슈머 전용 최종 예매 등록 메서드 (Consumer로부터 진입)
    public void createReservationFromQueue(InsertReservationRequest info) {
        repository.insert(info);
        log.info("[DB INSERT 완료 (eventId : {}), (userId : {})]", info.getEventId(), info.getUserId());

        // 데이터베이스에 예매 내역 저장 후. redis에 해당 공연에 몇 매를 예매했는 지 저장.
        String type = "PLUS";
        seatService.updateUserPurchaseLimit(
                info.getEventId(),
                info.getUserId(),
                info.getSeats().size(),
                type
        );
    }

    public ReservationResponse selectById(long id) {
        return repository.selectById(id).toResponse();
    }

    public CustomPageResponse<ReservationResponse> selectByCond(ReservationCondRequest cond) {
        Pageable pageable = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));

        Page<ReservationResponse> reservationPage = repository.selectByCond(cond, pageable).map(Reservation::toResponse);

        return CustomPageResponse.of(
                reservationPage.getContent(),
                reservationPage.getSize(),
                reservationPage.getNumber(),
                reservationPage.getTotalElements(),
                reservationPage.getTotalPages()
        );
    }

    public void cancel(long id, CancelReservationRequest info) {
        repository.cancel(id, info);

        // 레디스에 취소한 표만큼 매수를 줄임.
        String type = "SUB";
        seatService.updateUserPurchaseLimit(
                info.getEventId(),
                info.getUserId(),
                info.getSelectedTicketIdList().size(),
                type
        );
    }

    public void isReservable(IsReservableRequest info) {
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
