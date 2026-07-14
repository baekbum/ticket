package dev.bum.ticket_service.jpa.reservation;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.ticket.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.enums.DiscountType;
import dev.bum.common.service.ticket.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.exception.reservation.ReservationNotExistException;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.jpa.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.ReservationDiscount;
import dev.bum.ticket_service.jpa.coupon.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.coupon.UserCoupon;
import dev.bum.ticket_service.jpa.coupon.UserCouponJpaRepository;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventRepository;
import dev.bum.ticket_service.jpa.event.QEvent;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatRepository;
import dev.bum.ticket_service.jpa.ticket.QTicket;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final JPAQueryFactory queryFactory;
    private final ReservationJpaRepository jpaRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final ReservationDiscountJpaRepository reservationDiscountJpaRepository;
    private final EntityManager em;
    private QReservation reservation;

    /**
     * 예매 메서드
     * @param info
     */
    @Override
    public Reservation insert(InsertReservationRequest info) {
        // 1. 추가적으로 티켓팅이 가능한지 확인
        validateReservableFromDatabase(info.getUserId(), info.getEventId(), info.getSeats().size());

        // 2. 공연 정보 조회
        Event event = eventRepository.selectById(info.getEventId());

        // 3. 조회한 공연 정보를 통해 예매 정보(프레임)를 생성
        Reservation reservation = new Reservation(info, event);

        // 4. 선택한 좌석 검증 및 비관적 락(NOWAIT)으로 안전하게 선점 조회
        // (개수 불일치, 락 획득 실패 시 내부에서 알아서 예외 발생 및 전역 처리)
        List<Seat> seats = seatRepository.selectBySeatList(info.getEventId(), info.getSeats());
        List<Ticket> tickets = new ArrayList<>();
        int totalTicketAmount = calculateTotalTicketAmount(seats);
        DiscountSnapshot discountSnapshot = applyCouponIfRequested(info, reservation, totalTicketAmount);

        // 5. 검증이 끝난 좌석들의 상태를 RESERVED로 변경하고 티켓 생성
        for (Seat seat : seats) {
            seat.reserved();

            Ticket ticket = Ticket.builder()
                    .userId(info.getUserId())
                    .reservation(reservation)
                    .event(event)
                    .seat(seat)
                    .status(TicketStatus.PENDING_PAYMENT)
                    .build();

            tickets.add(ticket);
        }

        // 6. 변경된 좌석 상태(LOCKED)를 DB에 즉시 반영하여 물리적 선점 확정
        em.flush();

        // 7. 티켓과 예매 내역 저장
        Reservation savedReservation = jpaRepository.save(reservation);
        ticketRepository.insert(tickets);
        saveReservationDiscount(savedReservation, discountSnapshot);

        return savedReservation;
    }

    /**
     * ID로 예매 내역 조회
     * @param id
     * @return
     */
    @Override
    public Reservation selectById(long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new ReservationNotExistException("해당 예매 정보는 존재하지 않습니다."));
    }

    /**
     * 조건으로 예매 내역 조회
     * @param cond
     * @param pageable
     * @return
     */
    @Override
    public Page<Reservation> selectByCond(ReservationCondRequest cond, Pageable pageable) {
        reservation = QReservation.reservation;
        QEvent event = QEvent.event;
        QTicket ticket = QTicket.ticket;

        // 1. Pageable 객체에서 Sort 정보를 추출하여 OrderSpecifier 리스트를 생성
        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();
            PathBuilder<Reservation> entityPath = new PathBuilder<>(Reservation.class, "reservation");
            orderSpecifiers.add(new OrderSpecifier(direction, entityPath.get(property)));
        });

        Event targetEvent = cond.getEventId() > 0 ? eventRepository.selectById(cond.getEventId()) : null;

        // 1. 컨텐츠 조회
        List<Reservation> content = queryFactory
                .selectFrom(reservation)
                .distinct()
                .join(reservation.event, event).fetchJoin() // 공연 정보는 상세 내역에 필요하므로 FetchJoin
                .leftJoin(reservation.tickets, ticket)
                .where(
                        userIdEq(cond.getUserId()),
                        eventEq(targetEvent), // Reservation에 있는 event_id로 바로 필터링
                        seatIdEq(ticket, cond.getSeatId()),
                        dateBetween(cond.getStartDate(), cond.getEndDate()),
                        statusEq(cond.getStatus())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .fetch();

        // 2. 카운트 쿼리
        Long total = queryFactory
                .select(reservation.countDistinct())
                .from(reservation)
                .leftJoin(reservation.tickets, ticket)
                .where(
                        userIdEq(cond.getUserId()),
                        eventEq(targetEvent),
                        seatIdEq(ticket, cond.getSeatId()),
                        dateBetween(cond.getStartDate(), cond.getEndDate()),
                        statusEq(cond.getStatus())
                )
                .fetchOne();

        long totalCount = (total != null) ? total : 0L;

        return new PageImpl<>(content, pageable, totalCount);
    }

    /**
     * 예매 취소 메서드
     * @param id
     */
    @Override
    public List<Seat> cancel(long id, CancelReservationRequest info) {
        // 1. 예매, 티켓 정보 조회.
        Reservation foundReservation = selectById(id);
        List<Long> selectedTicketIdList = info.getSelectedTicketIdList();

        List<Ticket> tickets = selectedTicketIdList.isEmpty()
                ? ticketRepository.selectByReservation(foundReservation)
                : ticketRepository.selectByIdList(selectedTicketIdList);

        // 2. 티켓과 좌석 상태 변경
        for (Ticket ticket : tickets) {
            // 티켓 상태를 cancelled로 변경
            ticket.cancel();

            // 좌석 상태를 available로 변경
            ticket.getSeat().available();
        }

        // 3. 해당 예매에 속한 전체 티켓 중 유효한 티켓이 하나라도 남아있는지 확인
        List<TicketStatus> activeStatuses = List.of(
                TicketStatus.PENDING_PAYMENT,
                TicketStatus.PAID
        );

        boolean hasActiveTicket = foundReservation.getTickets().stream()
                .anyMatch(ticket -> activeStatuses.contains(ticket.getStatus()));

        // 4. 판단 결과에 따라 예매 상태 변경
        if (hasActiveTicket) {
            foundReservation.partial_cancel(); // 여전히 유효한 티켓이 있다면 부분 취소
        } else {
            foundReservation.cancel();        // 모든 티켓이 취소되었다면 전체 취소
            restoreUsedCoupons(foundReservation);
        }

        return tickets.stream()
                .map(Ticket::getSeat)
                .collect(Collectors.toList());
    }

    private int calculateTotalTicketAmount(List<Seat> seats) {
        return seats.stream()
                .mapToInt(seat -> seat.getPrice() != null ? seat.getPrice() : 0)
                .sum();
    }

    private DiscountSnapshot applyCouponIfRequested(InsertReservationRequest info, Reservation reservation, int totalTicketAmount) {
        if (info.getUserCouponId() == null) {
            return null;
        }

        UserCoupon userCoupon = userCouponJpaRepository.findById(info.getUserCouponId())
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 쿠폰이 존재하지 않습니다."));
        Coupon coupon = userCoupon.getCoupon();
        LocalDateTime now = LocalDateTime.now();

        validateUserCoupon(info, userCoupon, coupon, totalTicketAmount, now);

        int discountAmount = calculateDiscountAmount(coupon, totalTicketAmount);
        if (discountAmount <= 0) {
            throw new IllegalArgumentException("쿠폰 할인 금액이 유효하지 않습니다.");
        }

        userCoupon.use(now);

        return new DiscountSnapshot(
                userCoupon,
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                discountAmount
        );
    }

    private void validateUserCoupon(InsertReservationRequest info, UserCoupon userCoupon, Coupon coupon, int totalTicketAmount, LocalDateTime now) {
        if (!info.getUserId().equals(userCoupon.getUserId())) {
            throw new IllegalArgumentException("해당 사용자의 쿠폰이 아닙니다.");
        }

        if (userCoupon.getStatus() != UserCouponStatus.ISSUED) {
            throw new IllegalArgumentException("이미 사용했거나 사용할 수 없는 쿠폰입니다.");
        }

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new IllegalArgumentException("활성화된 쿠폰이 아닙니다.");
        }

        if (coupon.getValidFrom() != null && coupon.getValidFrom().isAfter(now)) {
            throw new IllegalArgumentException("아직 사용할 수 없는 쿠폰입니다.");
        }

        if (coupon.getValidUntil() != null && coupon.getValidUntil().isBefore(now)) {
            throw new IllegalArgumentException("만료된 쿠폰입니다.");
        }

        if (userCoupon.getExpiresAt() != null && userCoupon.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("만료된 쿠폰입니다.");
        }

        if (coupon.getMinOrderAmount() != null && totalTicketAmount < coupon.getMinOrderAmount()) {
            throw new IllegalArgumentException("쿠폰 최소 주문 금액을 충족하지 못했습니다.");
        }
    }

    private int calculateDiscountAmount(Coupon coupon, int totalTicketAmount) {
        int discountAmount;

        if (coupon.getDiscountType() == CouponDiscountType.FIXED_AMOUNT) {
            discountAmount = coupon.getDiscountValue();
        } else if (coupon.getDiscountType() == CouponDiscountType.PERCENT) {
            discountAmount = totalTicketAmount * coupon.getDiscountValue() / 100;
            if (coupon.getMaxDiscountAmount() != null) {
                discountAmount = Math.min(discountAmount, coupon.getMaxDiscountAmount());
            }
        } else {
            throw new IllegalArgumentException("지원하지 않는 쿠폰 할인 방식입니다.");
        }

        return Math.min(discountAmount, totalTicketAmount);
    }

    private void saveReservationDiscount(Reservation reservation, DiscountSnapshot discountSnapshot) {
        if (discountSnapshot == null || discountSnapshot.getDiscountAmount() <= 0) {
            return;
        }

        reservationDiscountJpaRepository.save(ReservationDiscount.builder()
                .reservation(reservation)
                .userCoupon(discountSnapshot.getUserCoupon())
                .discountType(DiscountType.COUPON)
                .discountName(discountSnapshot.getDiscountName())
                .couponDiscountType(discountSnapshot.getCouponDiscountType())
                .discountValue(discountSnapshot.getDiscountValue())
                .discountAmount(discountSnapshot.getDiscountAmount())
                .build());
    }

    private void restoreUsedCoupons(Reservation reservation) {
        List<ReservationDiscount> discounts = reservationDiscountJpaRepository.findByReservation(reservation);
        LocalDateTime now = LocalDateTime.now();

        for (ReservationDiscount discount : discounts) {
            UserCoupon userCoupon = discount.getUserCoupon();
            if (userCoupon != null && userCoupon.getStatus() == UserCouponStatus.USED) {
                userCoupon.restore(now);
            }
        }
    }

    /**
     * 유저가 특정 공연에 추가적으로 예매가 가능한지 확인하는 메서드
     * @param userId
     * @param eventId
     * @return
     */
    @Override
    public void validateReservableFromDatabase(String userId, long eventId, int selectedSeatCnt) {
        Event event = eventRepository.selectById(eventId);

        // 선택한 좌석이 1인 제한 매수보다 큰 경우.
        // ex) 매수 제한은 4매인데, 좌석을 5석 선택한 경우.
        if (event.getMaxTicketsPerPerson() < selectedSeatCnt) {
            throw new TicketLimitExceededException(
                    String.format("1인당 최대 예매 가능 수량은 %d매입니다.", event.getMaxTicketsPerPerson())
            );
        }

        // 매수 제한은 4매인데, 이미 2좌석을 선택했고 3좌석 이상을 추가적으로 티켓팅 하는 경우
        if (!ticketRepository.isWithinPurchaseLimit(userId, event, selectedSeatCnt)) {
            throw new TicketLimitExceededException(
                    String.format("이미 기존 예매 내역이 존재하여, 추가로 %d매를 초과하여 예매할 수 없습니다.", event.getMaxTicketsPerPerson())
            );
        }

    }

    // QueryDsl 동적 쿼리 관련 메서드
    private BooleanExpression userIdEq(String userId) {
        return StringUtils.hasText(userId) ? reservation.userId.eq(userId) : null;
    }

    private BooleanExpression eventEq(Event event) {
        if (event == null) return null;

        return reservation.event.eq(event);
    }

    private BooleanExpression seatIdEq(QTicket ticket, long seatId) {
        return seatId > 0 ? ticket.seat.seatId.eq(seatId) : null;
    }

    private BooleanExpression dateBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return null;

        // startDate 00:00:00 부터
        LocalDateTime startDateTime = startDate.atStartOfDay();
        // endDate 23:59:59.999999999 까지
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        return reservation.updatedAt.between(startDateTime, endDateTime);
    }

    private BooleanExpression statusEq(ReservationStatus status) {
        return status != null ? reservation.status.eq(status) : null;
    }

    private static class DiscountSnapshot {
        private final UserCoupon userCoupon;
        private final String discountName;
        private final CouponDiscountType couponDiscountType;
        private final Integer discountValue;
        private final Integer discountAmount;

        private DiscountSnapshot(UserCoupon userCoupon, String discountName, CouponDiscountType couponDiscountType, Integer discountValue, Integer discountAmount) {
            this.userCoupon = userCoupon;
            this.discountName = discountName;
            this.couponDiscountType = couponDiscountType;
            this.discountValue = discountValue;
            this.discountAmount = discountAmount;
        }

        private UserCoupon getUserCoupon() {
            return userCoupon;
        }

        private String getDiscountName() {
            return discountName;
        }

        private CouponDiscountType getCouponDiscountType() {
            return couponDiscountType;
        }

        private Integer getDiscountValue() {
            return discountValue;
        }

        private Integer getDiscountAmount() {
            return discountAmount;
        }
    }
}
