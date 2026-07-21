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

    @AuditLog(action = "RESERVATION_DELIVERY_CREATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse insert(long reservationId, ReservationDeliveryRequest info) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateNotExists(reservation);

        return reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, info)).toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_CREATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse insertMyDelivery(String currentUserId, long reservationId, ReservationDeliveryRequest info) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateOwner(currentUserId, reservation);
        validateNotExists(reservation);

        return reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, info)).toResponse();
    }

    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectById(long id) {
        return selectEntityById(id).toResponse();
    }

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

    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectMyById(String currentUserId, long id) {
        ReservationDelivery delivery = selectEntityById(id);
        validateOwner(currentUserId, delivery.getReservation());

        return delivery.toResponse();
    }

    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectByReservationId(long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);

        return reservationDeliveryJpaRepository.findByReservation(reservation)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약의 배송 정보가 존재하지 않습니다."))
                .toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_PREPARE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse prepare(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.prepare();
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_TRACKING_UPDATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse updateTracking(long id, UpdateReservationDeliveryTrackingRequest info) {
        ReservationDelivery delivery = selectEntityById(id);
        AuditDataMapper.setChangedData(delivery, info, "trackingNumber");
        delivery.updateTracking(info.getCarrier(), info.getTrackingNumber());
        return delivery.toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_UPDATE", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse updateMyDelivery(String currentUserId, long id, ReservationDeliveryRequest info) {
        ReservationDelivery delivery = selectEntityById(id);
        validateOwner(currentUserId, delivery.getReservation());
        AuditDataMapper.setChangedData(delivery, info, "recipientName", "recipientPhone", "address", "detailAddress");
        delivery.updateDeliveryInfo(info);
        return delivery.toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_SHIP", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse ship(long id, UpdateReservationDeliveryTrackingRequest info) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.ship(info != null ? info.getCarrier() : null, info != null ? info.getTrackingNumber() : null, LocalDateTime.now());
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_DELIVER", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse deliver(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.deliver(LocalDateTime.now());
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_RETURN", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse returnDelivery(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.returnDelivery();
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    @AuditLog(action = "RESERVATION_DELIVERY_CANCEL", targetType = "RESERVATION_DELIVERY")
    public ReservationDeliveryResponse cancel(long id) {
        ReservationDelivery delivery = selectEntityById(id);
        Object beforeStatus = delivery.getStatus();
        delivery.cancel();
        AuditDataMapper.setFieldChange("status", beforeStatus, delivery.getStatus());
        return delivery.toResponse();
    }

    @Transactional(readOnly = true)
    public ReservationDeliveryResponse selectMyByReservationId(String currentUserId, long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateOwner(currentUserId, reservation);

        return reservationDeliveryJpaRepository.findByReservation(reservation)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약의 배송 정보가 존재하지 않습니다."))
                .toResponse();
    }

    private ReservationDelivery selectEntityById(long id) {
        return reservationDeliveryJpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 배송 정보가 존재하지 않습니다."));
    }

    private void validateNotExists(Reservation reservation) {
        if (reservationDeliveryJpaRepository.existsByReservation(reservation)) {
            throw new IllegalArgumentException("이미 등록된 예약 배송 정보가 존재합니다.");
        }
    }

    private void validateOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약의 배송 정보만 처리할 수 있습니다.");
        }
    }

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
