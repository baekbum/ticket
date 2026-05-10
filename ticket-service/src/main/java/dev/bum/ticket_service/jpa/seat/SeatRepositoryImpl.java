package dev.bum.ticket_service.jpa.seat;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.exception.SeatDuplicateException;
import dev.bum.ticket_service.exception.SeatNotExistException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventRepository;
import dev.bum.ticket_service.jpa.event.QEvent;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
import dev.bum.ticket_service.vo.seat.UpdateSeatInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SeatRepositoryImpl implements SeatRepository {

    private final JPAQueryFactory queryFactory;
    private final SeatJpaRepository jpaRepository;
    private final EventRepository  eventRepository;
    private QSeat seat;

    @Override
    public Seat insert(InsertSeatInfo info) {
        SeatCond cond = SeatCond.builder()
                .eventId(info.getEventId())
                .seatNumber(info.getSeatNumber())
                .build();

        isExist(cond);

        info.setEvent(eventRepository.selectById(info.getEventId()));
        Seat seat = new Seat(info);
        jpaRepository.save(seat);
        return seat;
    }

    @Override
    public void isExist(SeatCond cond) {
        seat = QSeat.seat;

        List<Seat> found = queryFactory
                .select(seat)
                .from(seat)
                .where(
                        eventIdEq(cond.getEventId()),
                        seatNumberEq(cond.getSeatNumber())
                )
                .fetch();

        if (!found.isEmpty()) {
            throw new SeatDuplicateException("해당 좌석은 이미 예매 중입니다.");
        }
    }

    @Override
    public Seat selectById(Long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new SeatNotExistException("해당 좌석 정보는 존재하지 않습니다."));
    }

    @Override
    public Page<Seat> selectByCond(SeatCond cond, Pageable pageable) {
        seat = QSeat.seat;
        QEvent event = QEvent.event;

        // 1. Pageable 객체에서 Sort 정보를 추출하여 OrderSpecifier 리스트를 생성
        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();
            PathBuilder<Seat> entityPath = new PathBuilder<>(Seat.class, "seat");
            orderSpecifiers.add(new OrderSpecifier(direction, entityPath.get(property)));
        });

        List<Seat> content = queryFactory
                .selectFrom(seat)
                .join(seat.event, event).fetchJoin()
                .where(
                        seatIdEq(cond.getSeatId()),
                        eventIdEq(cond.getEventId()),
                        seatNumberEq(cond.getSeatNumber()),
                        gradeEq(cond.getGrade()),
                        statusEq(cond.getStatus())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(seat.seatId.asc()) // 기본 정렬
                .fetch();

        Long total = queryFactory
                .select(seat.count())
                .from(seat)
                .join(seat.event, event)
                .where(
                        seatIdEq(cond.getSeatId()),
                        eventIdEq(cond.getEventId()),
                        seatNumberEq(cond.getSeatNumber()),
                        gradeEq(cond.getGrade()),
                        statusEq(cond.getStatus())
                )
                .fetchOne();

        long totalCount = (total != null) ? total : 0L;

        return new PageImpl<>(content, pageable, totalCount);
    }

    @Override
    public Seat update(Long id, UpdateSeatInfo info) {
        Seat seat = selectById(id);
        seat.update(info);

        return seat;
    }

    @Override
    public Seat delete(Long id) {
        Seat seat = selectById(id);
        jpaRepository.delete(seat);

        return seat;
    }

    // QueryDsl 동적 쿼리 관련 메서드
    private BooleanExpression seatIdEq(Long seatId) {
        return seatId != null ? seat.seatId.eq(seatId) : null;
    }

    private BooleanExpression eventIdEq(Long eventId) {
        if (eventId == null) return null;

        Event event = eventRepository.selectById(eventId);

        return seat.event.eq(event);
    }

    private BooleanExpression seatNumberEq(String seatNumber) {
        return StringUtils.hasText(seatNumber) ? seat.seatNumber.eq(seatNumber) : null;
    }

    private BooleanExpression gradeEq(SeatGrade grade) {
        return grade != null ? seat.grade.eq(grade) : null;
    }

    private BooleanExpression statusEq(SeatStatus status) {
        return status != null ? seat.status.eq(status) : null;
    }
}
