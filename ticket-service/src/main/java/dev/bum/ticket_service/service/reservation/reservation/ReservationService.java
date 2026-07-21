package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDetailResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.dto.UpdateReservationStatusRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDelivery;
import dev.bum.ticket_service.jpa.reservation.reservationDelivery.ReservationDeliveryJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
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
import java.util.Optional;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository repository;
    private final SeatCacheService seatCacheService;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final ReservationDeliveryJpaRepository reservationDeliveryJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;

    /**
     * 예매 ID로 예매 기본 정보를 조회한다.
     */
    public ReservationResponse selectById(long id) {
        return repository.selectById(id).toResponse();
    }

    /**
     * 예매 기본 정보와 티켓, 할인, 배송, 결제 요약을 함께 조회한다.
     */
    public ReservationDetailResponse selectDetailById(long id) {
        Reservation reservation = repository.selectById(id);
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        int totalTicketAmount = tickets.stream()
                .mapToInt(ticket -> ticket.getPrice() != null ? ticket.getPrice() : 0)
                .sum();
        int totalDiscountAmount = discounts.stream()
                .mapToInt(discount -> discount.getDiscountAmount() != null ? discount.getDiscountAmount() : 0)
                .sum();

        return ReservationDetailResponse.builder()
                .reservation(reservation.toResponse())
                .tickets(tickets.stream().map(Ticket::toResponse).toList())
                .discounts(discounts.stream().map(ReservationDiscount::toResponse).toList())
                .delivery(reservationDeliveryJpaRepository.findByReservation(reservation)
                        .map(delivery -> delivery.toResponse())
                        .orElse(null))
                .payment(paymentJpaRepository.findByReservation(reservation)
                        .map(payment -> payment.toResponse())
                        .orElse(null))
                .totalTicketAmount(totalTicketAmount)
                .totalDiscountAmount(totalDiscountAmount)
                .paymentAmount(Math.max(totalTicketAmount - totalDiscountAmount, 0))
                .build();
    }

    /**
     * 로그인 사용자가 본인 예매 기본 정보를 조회한다.
     */
    public ReservationResponse selectMyReservation(String currentUserId, long id) {
        Reservation reservation = repository.selectById(id);
        validateOwner(currentUserId, reservation);
        return reservation.toResponse();
    }

    /**
     * 관리자 검색 조건과 페이징 조건으로 예매 목록을 조회한다.
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

    /**
     * 로그인 사용자 ID를 검색 조건에 주입해 본인 예매 목록을 조회한다.
     */
    public CustomPageResponse<ReservationResponse> selectMyReservations(String currentUserId, ReservationCondRequest cond) {
        cond.setUserId(currentUserId);
        return selectByCond(cond);
    }

    /**
     * 관리자 기준으로 예매 티켓을 취소하고 좌석 캐시와 사용자 구매 수량을 보정한다.
     */
    @AuditLog(action = "RESERVATION_CANCEL", targetType = "RESERVATION")
    public void cancel(long id, CancelReservationRequest info) {
        List<Seat> cancelledSeats = repository.cancel(id, info);

        seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);
        seatCacheService.updateUserPurchaseLimit(
                info.getEventId(),
                info.getUserId(),
                cancelledSeats.size(),
                "SUB"
        );
    }

    /**
     * 로그인 사용자가 본인 예매를 취소한다.
     */
    @AuditLog(action = "RESERVATION_CANCEL", targetType = "RESERVATION")
    public void cancelMyReservation(String currentUserId, long id, CancelReservationRequest info) {
        Reservation reservation = repository.selectById(id);
        Object beforeStatus = reservation.getStatus();
        validateOwner(currentUserId, reservation);
        info.setUserId(currentUserId);
        cancel(id, info);
        AuditDataMapper.setFieldChange("status", beforeStatus, "CANCELLED");
    }

    /**
     * 관리자 상태 보정 요청에 따라 예매, 티켓, 좌석, 결제, 배송, 쿠폰 상태를 함께 정정한다.
     */
    @AuditLog(action = "RESERVATION_STATUS_ADJUST", targetType = "RESERVATION")
    public ReservationDetailResponse updateStatus(long id, UpdateReservationStatusRequest request) {
        Reservation reservation = repository.selectById(id);
        ReservationStatus beforeStatus = reservation.getStatus();
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        Optional<ReservationDelivery> delivery = reservationDeliveryJpaRepository.findByReservation(reservation);
        Optional<Payment> payment = paymentJpaRepository.findByReservation(reservation);

        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("상태를 보정할 티켓 정보가 없습니다.");
        }

        ReservationStatus targetStatus = request.getStatus();
        List<Seat> lockedSeats = new ArrayList<>();
        List<Seat> reservedSeats = new ArrayList<>();
        List<Seat> availableSeats = new ArrayList<>();

        switch (targetStatus) {
            case PENDING_PAYMENT -> {
                reservation.pendingPayment();
                tickets.forEach(ticket -> {
                    ticket.pendingPayment();
                    ticket.getSeat().lock();
                    lockedSeats.add(ticket.getSeat());
                });
                payment.ifPresent(Payment::ready);
                delivery.ifPresent(ReservationDelivery::ready);
            }
            case PAID -> {
                reservation.paid();
                tickets.forEach(ticket -> {
                    ticket.paid();
                    ticket.getSeat().reserved();
                    reservedSeats.add(ticket.getSeat());
                });
                payment.ifPresent(foundPayment -> foundPayment.complete(null));
                delivery.ifPresent(ReservationDelivery::ready);
            }
            case PARTIALLY_CANCELLED -> {
                List<Ticket> selectedTickets = selectTicketsForPartialCancel(tickets, request.getSelectedTicketIdList());
                if (selectedTickets.size() == tickets.size()) {
                    throw new IllegalArgumentException("전체 티켓 취소는 CANCELLED 상태로 보정해주세요.");
                }

                selectedTickets.forEach(ticket -> {
                    ticket.cancel();
                    ticket.getSeat().available();
                    availableSeats.add(ticket.getSeat());
                });

                tickets.stream()
                        .filter(ticket -> !selectedTickets.contains(ticket))
                        .forEach(ticket -> {
                            ticket.paid();
                            ticket.getSeat().reserved();
                            reservedSeats.add(ticket.getSeat());
                        });

                reservation.partial_cancel();
                payment.ifPresent(foundPayment -> foundPayment.complete(null));
            }
            case CANCELLED -> {
                reservation.cancel();
                tickets.forEach(ticket -> {
                    ticket.cancel();
                    ticket.getSeat().available();
                    availableSeats.add(ticket.getSeat());
                });
                delivery.ifPresent(ReservationDelivery::cancel);
                payment.ifPresent(foundPayment -> {
                    if (foundPayment.getStatus() == PaymentStatus.PAID) {
                        foundPayment.refund();
                    } else {
                        foundPayment.cancel();
                    }
                });
                restoreUsedCoupons(reservation);
            }
            case EXPIRED -> {
                reservation.expire();
                tickets.forEach(ticket -> {
                    ticket.expire();
                    ticket.getSeat().available();
                    availableSeats.add(ticket.getSeat());
                });
                delivery.ifPresent(ReservationDelivery::cancel);
                payment.ifPresent(Payment::expire);
                restoreUsedCoupons(reservation);
            }
        }

        AuditDataMapper.setFieldChange("status", beforeStatus, targetStatus);
        syncAdjustedSeatCache(lockedSeats, reservedSeats, availableSeats);

        return selectDetailById(id);
    }

    /**
     * 부분 취소 대상으로 선택된 티켓이 현재 예매에 속하는지 검증하고 반환한다.
     */
    private List<Ticket> selectTicketsForPartialCancel(List<Ticket> tickets, List<Long> selectedTicketIdList) {
        if (selectedTicketIdList == null || selectedTicketIdList.isEmpty()) {
            throw new IllegalArgumentException("부분 취소는 취소할 티켓을 선택해야 합니다.");
        }

        List<Ticket> selectedTickets = tickets.stream()
                .filter(ticket -> selectedTicketIdList.contains(ticket.getTicketId()))
                .toList();

        if (selectedTickets.size() != selectedTicketIdList.size()) {
            throw new IllegalArgumentException("선택한 티켓 중 해당 예매에 속하지 않는 티켓이 있습니다.");
        }

        return selectedTickets;
    }

    /**
     * 상태 보정으로 변경된 좌석 목록을 Redis 캐시에 반영한다.
     */
    private void syncAdjustedSeatCache(List<Seat> lockedSeats, List<Seat> reservedSeats, List<Seat> availableSeats) {
        if (!lockedSeats.isEmpty()) {
            seatCacheService.syncLockedSeatsAfterCommit(lockedSeats);
        }
        if (!reservedSeats.isEmpty()) {
            seatCacheService.syncReservedSeatsAfterCommit(reservedSeats);
        }
        if (!availableSeats.isEmpty()) {
            seatCacheService.syncAvailableSeatsAfterCommit(availableSeats);
        }
    }

    /**
     * 전체 취소 또는 만료 처리 시 예매 할인 스냅샷에 연결된 사용 쿠폰을 복구한다.
     */
    private void restoreUsedCoupons(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() != null && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }

    /**
     * 요청 sort 문자열을 Spring Data Sort 객체로 변환한다.
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
     * 요청 사용자가 예매 소유자인지 검증한다.
     */
    private void validateOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약만 조회하거나 취소할 수 있습니다.");
        }
    }
}
