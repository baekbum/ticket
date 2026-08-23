package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.ticket_service.audit.AuditDataMapper;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
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

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository repository;
    private final SeatCacheService seatCacheService;
    private final PaymentJpaRepository paymentJpaRepository;
    private final CardPaymentRefundService cardPaymentRefundService;
    private final VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;
    private final TicketJpaRepository ticketJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;

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
     * 로그인 사용자 ID를 검색 조건에 주입해 본인 예매 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<ReservationResponse> selectMyReservations(String currentUserId, ReservationCondRequest cond) {
        cond.setUserId(currentUserId);
        return selectByCond(cond);
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
        cancel(id, reservation, info);
        AuditDataMapper.setFieldChange("status", beforeStatus, "CANCELLED");
    }

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

    private void cancel(long id, Reservation reservation, CancelReservationRequest info) {
        validateCancelableReservation(reservation);
        List<Ticket> tickets = ticketJpaRepository.findByReservation(reservation);
        List<Ticket> activeTickets = selectActiveTickets(tickets);
        List<Ticket> selectedTickets = selectTicketsForCancel(activeTickets, info.getSelectedTicketIdList());
        boolean fullCancellation = isFullCancellation(activeTickets, selectedTickets);
        boolean restoreCouponOnCancel = fullCancellation && reservation.getStatus() == ReservationStatus.PAID;

        refundPaymentBeforeCancel(reservation, info, activeTickets, selectedTickets, fullCancellation);
        List<Seat> cancelledSeats = cancelTickets(selectedTickets);
        applyReservationCancelStatus(reservation, fullCancellation, restoreCouponOnCancel);

        seatCacheService.syncAvailableSeatsAfterCommit(cancelledSeats);
        if (!cancelledSeats.isEmpty()) {
            seatCacheService.updateUserPurchaseLimit(
                    cancelledSeats.get(0).getEvent(),
                    info.getUserId(),
                    cancelledSeats.size(),
                    "SUB"
            );
        }
    }

    /**
     * 결제 완료 예매를 취소하는 경우, 로컬 예매 상태를 바꾸기 전에 gateway 환불을 먼저 완료한다.
     */
    private void refundPaymentBeforeCancel(
            Reservation reservation,
            CancelReservationRequest info,
            List<Ticket> activeTickets,
            List<Ticket> selectedTickets,
            boolean fullCancellation
    ) {
        if (reservation.getStatus() != ReservationStatus.PAID
                && reservation.getStatus() != ReservationStatus.PARTIALLY_CANCELLED) {
            return;
        }

        paymentJpaRepository.findByReservation(reservation)
                .ifPresent(payment -> {
                    if (payment.getStatus() != PaymentStatus.PAID
                            && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                        return;
                    }

                    if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
                        if (fullCancellation) {
                            cardPaymentRefundService.refundAll(payment);
                            return;
                        } else {
                            cardPaymentRefundService.refundPartial(payment, calculatePartialRefundAmount(payment.getRefundableAmount(), activeTickets, selectedTickets));
                            return;
                        }

                    }

                    if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
                        if (fullCancellation) {
                            virtualAccountPaymentRefundService.refundAll(
                                    payment,
                                    info.getRefundAccount()
                            );
                            return;
                        } else {
                            virtualAccountPaymentRefundService.refundPartial(
                                    payment,
                                    calculatePartialRefundAmount(payment.getRefundableAmount(), activeTickets, selectedTickets),
                                    info.getRefundAccount()
                            );
                            return;
                        }
                    }
        });
    }

    private void validateCancelableReservation(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new IllegalArgumentException("이미 취소되었거나 만료된 예매입니다.");
        }
    }

    private boolean isFullCancellation(List<Ticket> activeTickets, List<Ticket> selectedTickets) {
        return selectedTickets.size() == activeTickets.size();
    }

    private List<Ticket> selectActiveTickets(List<Ticket> tickets) {
        return tickets.stream()
                .filter(this::isActiveTicket)
                .toList();
    }

    private List<Ticket> selectTicketsForCancel(List<Ticket> activeTickets, List<Long> selectedTicketIdList) {
        if (selectedTicketIdList == null || selectedTicketIdList.isEmpty()) {
            throw new IllegalArgumentException("취소할 티켓을 선택해야 합니다.");
        }
        if (selectedTicketIdList.size() != new HashSet<>(selectedTicketIdList).size()) {
            throw new IllegalArgumentException("취소할 티켓이 중복 선택되었습니다.");
        }

        List<Ticket> selectedTickets = activeTickets.stream()
                .filter(ticket -> selectedTicketIdList.contains(ticket.getTicketId()))
                .toList();

        if (selectedTickets.size() != selectedTicketIdList.size()) {
            throw new IllegalArgumentException("선택한 티켓 중 취소 가능한 예매 티켓이 아닌 항목이 있습니다.");
        }

        return selectedTickets;
    }

    /**
     * 환불 설명, 전체 취소 같은 경우는 쿠폰 복구, 부분 취소 같은 경우는 쿠폰 복구 X
     * 티켓 3장 × 100,000원 = totalTicketAmount 300,000
     * 쿠폰 할인 30,000원
     * 실제 결제금액 = 270,00
     * 1장을 부분 환불 한다고 가정
     * 270,000 × 100,000 / 300,000 = 90,000
     * 9만원 환불.
     * @param remainingPaymentAmount
     * @param activeTickets
     * @param selectedTickets
     * @return
     */
    private int calculatePartialRefundAmount(Integer remainingPaymentAmount, List<Ticket> activeTickets, List<Ticket> selectedTickets) {
        int totalTicketAmount = activeTickets.stream()
                .mapToInt(ticket -> ticket.getPrice() != null ? ticket.getPrice() : 0)
                .sum();

        int selectedTicketAmount = selectedTickets.stream()
                .mapToInt(ticket -> ticket.getPrice() != null ? ticket.getPrice() : 0)
                .sum();

        if (totalTicketAmount <= 0 || selectedTicketAmount <= 0) {
            throw new IllegalArgumentException("부분 환불 금액을 계산할 수 없습니다.");
        }

        // 부분 취소 시 쿠폰은 복구하지 않으므로, 남은 실결제금액을 남은 활성 티켓 정가 비율로 배분해 환불한다.
        return BigDecimal.valueOf(remainingPaymentAmount)
                .multiply(BigDecimal.valueOf(selectedTicketAmount))
                .divide(BigDecimal.valueOf(totalTicketAmount), 0, RoundingMode.DOWN)
                .intValueExact();
    }

    private boolean isActiveTicket(Ticket ticket) {
        return ticket.getStatus() == TicketStatus.PENDING_PAYMENT || ticket.getStatus() == TicketStatus.PAID;
    }

    private List<Seat> cancelTickets(List<Ticket> selectedTickets) {
        selectedTickets.forEach(ticket -> {
            ticket.cancel();
            ticket.getSeat().available();
        });

        return selectedTickets.stream()
                .map(Ticket::getSeat)
                .toList();
    }

    private void applyReservationCancelStatus(Reservation reservation, boolean fullCancellation, boolean restoreCouponOnCancel) {
        if (!fullCancellation) {
            reservation.partial_cancel();
            return;
        }

        reservation.cancel();
        if (restoreCouponOnCancel) {
            restoreUsedCoupons(reservation);
        }
    }

    private void restoreUsedCoupons(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);

        for (ReservationDiscount discount : discounts) {
            if (discount.getUserCoupon() != null && discount.getUserCoupon().getStatus() == UserCouponStatus.USED) {
                discount.getUserCoupon().restore(now);
            }
        }
    }

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
