package dev.bum.ticket_service.service.reservation.delivery;

import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class ReservationDeliveryService {

    private final ReservationRepository reservationRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;

    public ReservationDeliveryResponse insert(long reservationId, ReservationDeliveryRequest info) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateNotExists(reservation);

        return reservationDeliveryJpaRepository.save(new ReservationDelivery(reservation, info)).toResponse();
    }

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
}
