package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDetailResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;

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

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    // 예매 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationRepository repository;
    // 예매 취소 업무 처리를 담당하는 서비스.
    private final ReservationCancellationService reservationCancellationService;
    // 예매 업무 처리를 담당하는 서비스.
    private final ReservationManagementService reservationManagementService;

    /**
     * 로그인 사용자가 본인 예매 기본 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public ReservationResponse selectMyReservation(String currentUserId, long id) {

        Reservation reservation = repository.selectById(id);
        validateOwner(currentUserId, reservation);
        return reservation.toResponse();
    }

    /**
     * 현재 사용자의 예매 상세 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public ReservationDetailResponse selectMyReservationDetail(String currentUserId, long id) {

        validateOwner(currentUserId, repository.selectById(id));
        return reservationManagementService.selectDetailById(id);
    }

    /**
     * 로그인 사용자 ID를 검색 조건에 주입해 본인 예매 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<ReservationResponse> selectMyReservations(
            String currentUserId,
            ReservationCondRequest cond
    ) {

        cond.setUserId(currentUserId);
        return selectByCond(cond);
    }

    /**
     * 로그인 사용자가 본인 예매를 취소한다.
     */
    @AuditLog(action = "RESERVATION_CANCEL", targetType = "RESERVATION")
    public CancelReservationResponse cancelMyReservation(String currentUserId, long id, CancelReservationRequest info) {

        Reservation reservation = repository.selectByIdForUpdate(id);
        Object beforeStatus = reservation.getStatus();
        validateOwner(currentUserId, reservation);
        info.setUserId(currentUserId);
        CancelReservationResponse response = reservationCancellationService.cancel(reservation, info);
        AuditDataMapper.setFieldChange("status", beforeStatus, reservation.getStatus());
        return response;
    }

    /**
     * 검색 조건에 맞는 예매 정보를 조회한다.
     */
    private CustomPageResponse<ReservationResponse> selectByCond(ReservationCondRequest cond) {

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

    /**
     * 검색 요청의 정렬 항목과 방향을 구성한다.
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

    /**
     * 요청자가 처리 대상의 소유자인지 검증한다.
     */
    private void validateOwner(String currentUserId, Reservation reservation) {

        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약만 조회하거나 취소할 수 있습니다.");
        }
    }
}
