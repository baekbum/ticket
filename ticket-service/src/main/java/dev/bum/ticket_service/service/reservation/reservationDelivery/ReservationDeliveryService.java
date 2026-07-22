package dev.bum.ticket_service.service.reservation.reservationDelivery;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.common.service.ticket.reservation.dto.UpdateReservationDeliveryTrackingRequest;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ReservationDeliveryService {

    private final ReservationRepository reservationRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    /**
     * 관리자 기준으로 예매에 배송 스냅샷을 등록한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_CREATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse insert(long reservationId, ReservationDeliveryRequest info) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateNotExists(reservation);

        return reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, info)).toResponse();
    }

    /**
     * 로그인 사용자가 본인 예매에 배송 스냅샷을 등록한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_CREATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse insertMyDelivery(String currentUserId, long reservationId, ReservationDeliveryRequest info) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateOwner(currentUserId, reservation);
        validateNotExists(reservation);

        return reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, info)).toResponse();
    }

    /**
     * 배송 ID로 배송 스냅샷을 조회한다.
     */
    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectById(long id) {
        return selectEntityById(id).toResponse();
    }

    /**
     * 관리자 검색 조건으로 배송 스냅샷 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<ReservationDeliveryResponse> selectByCond(ReservationDeliveryCondRequest cond) {
        PageRequest pageable = PageRequest.of(
                cond.getPage() != null ? cond.getPage() : 0,
                cond.getSize() != null ? cond.getSize() : 10,
                makeSort(cond.getSort())
        );

        Page<ReservationDeliveryResponse> page = reservationDeliveryJpaRepository
                .findAll(makeSpec(cond), pageable)
                .map(ReservationDelivery::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * 로그인 사용자가 본인 예매의 배송 스냅샷을 배송 ID로 조회한다.
     */
    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectMyById(String currentUserId, long id) {
        ReservationDelivery delivery = selectEntityById(id);
        validateOwner(currentUserId, delivery.getReservation());

        return delivery.toResponse();
    }

    /**
     * 예매 ID로 연결된 배송 스냅샷을 조회한다.
     */
    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectByReservationId(long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);

        return reservationDeliveryJpaRepository.findByReservation(reservation)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약의 배송 정보가 존재하지 않습니다."))
                .toResponse();
    }

    /**
     * 배송 상태를 출고 준비 중으로 변경한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_PREPARE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse prepare(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.prepare();
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    /**
     * 택배사와 운송장 번호를 저장한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_TRACKING_UPDATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse updateTracking(long id, UpdateReservationDeliveryTrackingRequest info) {
        ReservationDelivery delivery = selectEntityById(id);
        AuditDataMapper.setChangedData(delivery, info, "trackingNumber");
        delivery.updateTracking(info.getCarrier(), info.getTrackingNumber());
        return delivery.toResponse();
    }

    /**
     * 로그인 사용자가 배송 준비 전 본인 배송지 정보를 수정한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_UPDATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse updateMyDelivery(String currentUserId, long id, ReservationDeliveryRequest info) {
        ReservationDelivery delivery = selectEntityById(id);
        validateOwner(currentUserId, delivery.getReservation());
        AuditDataMapper.setChangedData(delivery, info, "recipientName", "recipientPhone", "address", "detailAddress");
        delivery.updateDeliveryInfo(info);
        return delivery.toResponse();
    }

    /**
     * 운송장 정보를 반영하고 배송 상태를 발송 완료로 변경한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_SHIP", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse ship(long id, UpdateReservationDeliveryTrackingRequest info) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.ship(info != null ? info.getCarrier() : null, info != null ? info.getTrackingNumber() : null, LocalDateTime.now());
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    /**
     * 배송 상태를 배송 완료로 변경한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_DELIVER", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse deliver(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.deliver(LocalDateTime.now());
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    /**
     * 배송 상태를 반송으로 변경한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_RETURN", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse returnDelivery(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.returnDelivery();
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    /**
     * 배송 상태를 취소로 변경한다.
     */
    @AuditLog(action = "RESERVATION_DELIVERY_CANCEL", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse cancel(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.cancel();
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    /**
     * 로그인 사용자가 본인 예매의 배송 스냅샷을 예매 ID로 조회한다.
     */
    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectMyByReservationId(String currentUserId, long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateOwner(currentUserId, reservation);

        return reservationDeliveryJpaRepository.findByReservation(reservation)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약의 배송 정보가 존재하지 않습니다."))
                .toResponse();
    }

    /**
     * 배송 ID로 엔티티를 조회하고 없으면 예외를 발생시킨다.
     */
    private ReservationDelivery selectEntityById(long id) {
        return reservationDeliveryJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 배송 정보가 존재하지 않습니다."));
    }

    /**
     * 예매에 배송 스냅샷이 이미 등록되어 있는지 검증한다.
     */
    private void validateNotExists(Reservation reservation) {
        if (reservationDeliveryJpaRepository.existsByReservation(reservation)) {
            throw new IllegalArgumentException("이미 등록된 예약 배송 정보가 존재합니다.");
        }
    }

    /**
     * 요청 사용자가 배송 스냅샷이 속한 예매의 소유자인지 검증한다.
     */
    private void validateOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약의 배송 정보만 처리할 수 있습니다.");
        }
    }

    /**
     * 배송 검색 조건을 JPA Specification으로 변환한다.
     */
    private Specification<ReservationDelivery> makeSpec(ReservationDeliveryCondRequest cond) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("reservation", JoinType.LEFT);
            }

            if (cond.getReservationId() != null && cond.getReservationId() > 0) {
                predicates.add(cb.equal(root.get("reservation").get("reservationId"), cond.getReservationId()));
            }
            if (StringUtils.hasText(cond.getUserId())) {
                predicates.add(cb.like(root.get("reservation").get("userId"), "%" + cond.getUserId() + "%"));
            }
            if (cond.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), cond.getStatus()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * 요청 sort 문자열을 Spring Data Sort 객체로 변환하고 기본 정렬을 적용한다.
     */
    private Sort makeSort(List<String> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "reservationDeliveryId");
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String sort : sorts) {
            String[] parts = sort.split("-");
            if (parts.length == 2) {
                orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]));
            }
        }

        return orders.isEmpty() ? Sort.by(Sort.Direction.DESC, "reservationDeliveryId") : Sort.by(orders);
    }
}
