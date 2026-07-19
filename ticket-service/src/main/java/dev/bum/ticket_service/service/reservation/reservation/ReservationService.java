package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.*;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository repository;
    private final SeatCacheService seatCacheService;
    /**
     * 예매 요청을 Kafka로 전달하기 전 Redis 좌석 선점 정보를 검증하는 메서드
     * @param info
     */
    public ReservationResponse insert(InsertReservationRequest info) {
        // 1. Redis의 좌석 선점 정보가 요청 사용자와 일치하는지 검증
        seatCacheService.validateOccupiedSeat(info);
        log.info("[좌석 선점 완료 (eventId : {}), (userId : {})]", info.getEventId(), info.getUserId());

        // 2. Kafka를 통해 최종 예매 등록을 비동기로 처리
        Reservation reservation = repository.insert(info);

        seatCacheService.updateUserPurchaseLimit(
                info.getEventId(),
                info.getUserId(),
                info.getSeats().size(),
                "PLUS"
        );

        return reservation.toResponse();
    }

    public ReservationResponse insertMyReservation(String currentUserId, InsertReservationRequest info) {
        info.setUserId(currentUserId);
        return insert(info);
    }

    /**
     * Kafka Consumer 전용 최종 예매 등록 메서드
     * @param info
     */
    public void createReservationFromQueue(InsertReservationRequest info) {
        // 1. DB에 예매 정보와 티켓 정보를 저장하고 좌석 상태를 RESERVED로 변경
        Reservation reservation = repository.insert(info);

        // 2. DB 커밋 이후 Redis 좌석 상태를 공연 종료 시점까지 RESERVED로 동기화
        List<Seat> seats = reservation.getTickets().stream()
                .map(ticket -> ticket.getSeat())
                .collect(Collectors.toList());
        seats.forEach(Seat::reserved);
        seatCacheService.syncReservedSeatsAfterCommit(seats);

        log.info("[DB INSERT 완료 (eventId : {}), (userId : {})]", info.getEventId(), info.getUserId());

        // 3. Redis에 해당 사용자가 공연별로 예매한 매수 반영
        seatCacheService.updateUserPurchaseLimit(
                info.getEventId(),
                info.getUserId(),
                info.getSeats().size(),
                "PLUS"
        );
    }

    /**
     * ID로 예매 내역을 조회하는 메서드
     * @param id
     * @return
     */
    public ReservationResponse selectById(long id) {
        return repository.selectById(id).toResponse();
    }

    public ReservationResponse selectMyReservation(String currentUserId, long id) {
        Reservation reservation = repository.selectById(id);
        validateOwner(currentUserId, reservation);
        return reservation.toResponse();
    }

    /**
     * 조건으로 예매 내역을 조회하는 메서드
     * @param cond
     * @return
     */
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

    public CustomPageResponse<ReservationResponse> selectMyReservations(String currentUserId, ReservationCondRequest cond) {
        cond.setUserId(currentUserId);
        return selectByCond(cond);
    }

    /**
     * 예매 취소 처리 메서드
     * @param id
     * @param info
     */
    public void cancel(long id, CancelReservationRequest info) {
        // 1. 예매 취소 처리 후 취소된 티켓의 좌석 목록 조회
        List<Seat> cancelledSeats = repository.cancel(id, info);

        // 2. DB 커밋 이후 Redis 좌석 상태를 AVAILABLE로 동기화
        seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);

        // 3. Redis에 저장된 사용자별 예매 매수 차감
        seatCacheService.updateUserPurchaseLimit(
                info.getEventId(),
                info.getUserId(),
                cancelledSeats.size(),
                "SUB"
        );
    }

    public void cancelMyReservation(String currentUserId, long id, CancelReservationRequest info) {
        Reservation reservation = repository.selectById(id);
        validateOwner(currentUserId, reservation);
        info.setUserId(currentUserId);
        cancel(id, info);
    }

    /**
     * 사용자가 추가 예매 가능한지 검증하는 메서드
     * @param info
     */
    public void isReservable(IsReservableRequest info) {
        repository.validateReservableFromDatabase(info.getUserId(), info.getEventId(), info.getSelectedSeatCnt());
    }

    public void isMyReservable(String currentUserId, IsReservableRequest info) {
        info.setUserId(currentUserId);
        isReservable(info);
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

    private void validateOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약만 조회하거나 취소할 수 있습니다.");
        }
    }
}
