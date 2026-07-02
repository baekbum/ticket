package dev.bum.ticket_service.jpa.seat;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.InsertSeatAreaConfig;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.seat.vo.UpdateSeatAreaConfig;
import dev.bum.ticket_service.exception.seat.SeatDuplicateException;
import dev.bum.ticket_service.exception.seat.SeatNotExistException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventRepository;
import dev.bum.ticket_service.jpa.event.QEvent;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
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
    private final EntityManager entityManager;
    private QSeat seat;

    @Override
    public void insert(InsertSeatRequest info) {
        Long eventId = info.getEventId();

        // 공연 정보가 존재하는 지 확인.
        Event event = eventRepository.selectById(eventId);

        // 해당 공연 정보가 이미 등록되어있는지 확인.
        SeatCondRequest cond = SeatCondRequest.builder()
                .eventId(info.getEventId())
                .build();

        isExist(cond);

        // 데이터를 구역 별로 벌크 처리
        int batchSize = 500; // 500개 단위로 끊어서 처리
        int count = 0;

        for (InsertSeatAreaConfig config : info.getInsertSeatAreaConfigs()) {
            for (int r = 1; r <= config.getRows(); r++) {
                for (int c = 1; c <= config.getCols(); c++) {
                    Double positionX = calculatePosition(config.getStartX(), config.getGapX(), c);
                    Double positionY = calculatePosition(config.getStartY(), config.getGapY(), r);

                    Seat seat = Seat.builder()
                            .event(event)
                            .zone(config.getZone())
                            .seatRow(r)
                            .seatCol(c)
                            .grade(config.getGrade())
                            .price(config.getPrice())
                            .status(SeatStatus.AVAILABLE)
                            .positionX(positionX)
                            .positionY(positionY)
                            .rotation(config.getRotation())
                            .build();

                    jpaRepository.save(seat); // 하나씩 save 호출 (실제 쿼리는 batch 옵션에 따라 모임)

                    if (++count % batchSize == 0) {
                        entityManager.flush(); // DB에 쿼리 전송
                        entityManager.clear(); // 1차 캐시 비우기 (메모리 확보)
                    }
                }
            }
        }
    }

    private Double calculatePosition(Double start, Double gap, int index) {
        if (start == null || gap == null) {
            return null;
        }

        return start + ((index - 1) * gap);
    }

    @Override
    public void isExist(SeatCondRequest cond) {
        seat = QSeat.seat;

        List<Seat> found = queryFactory
                .select(seat)
                .from(seat)
                .where(
                        eventIdEq(cond.getEventId()),
                        zoneEq(cond.getZone()),
                        seatRowEq(cond.getSeatRow()),
                        seatColEq(cond.getSeatCol()),
                        statusNotAvailable()
                )
                .fetch();

        if (!found.isEmpty()) {
            throw new SeatDuplicateException("해당 좌석은 이미 예매 중입니다.");
        }
    }

    @Override
    public long countByEventId(Long eventId) {
        return jpaRepository.countByEventEventId(eventId);
    }

    @Override
    public Seat selectById(Long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new SeatNotExistException("해당 좌석 정보는 존재하지 않습니다."));
    }

    @Override
    public List<Seat> selectByEventId(Long eventId) {
        List<Seat> seats = jpaRepository.findByEventEventId(eventId);

        if (seats.isEmpty()) throw new SeatNotExistException("해당 좌석 정보는 존재하지 않습니다.");

        return seats;
    }

    @Override
    public List<Seat> selectBySeatList(List<SeatInfo> seatInfos) {
        List<Long> idList = seatInfos.stream()
                .map(SeatInfo::getId)
                .toList();

        try {
            // 1. AVAILABLE 상태이면서 현재 아무도 락을 쥐고 있지 않은 좌석만 조회
            List<Seat> seats = jpaRepository.findAllBySeatIdInAndStatus(idList, SeatStatus.AVAILABLE);

            // 2. 아예 조회된 게 없다면 (잘못된 ID 번호거나, 전부 이미 예매 완료된 상태)
            if (seats.isEmpty()) {
                throw new SeatNotExistException("해당 좌석 정보는 존재하지 않습니다.");
            }

            // 3. 요청한 개수와 조회된 개수가 다르면 (일부는 이미 예매 완료 상태)
            if (idList.size() != seats.size()) {
                throw new SeatDuplicateException("이미 선택되었거나 예매할 수 없는 좌석이 포함되어 있습니다.");
            }

            return seats;

        } catch (PessimisticLockingFailureException e) {
            // 4. 대기 시간 0초(NOWAIT) 상태에서 누군가 이미 선점 중이라 락 획득에 실패한 경우
            throw new SeatDuplicateException("이미 다른 사용자가 결제 시도 중인 좌석이 포함되어 있습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Override
    public Page<Seat> selectByCond(SeatCondRequest cond, Pageable pageable) {
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
                        zoneEq(cond.getZone()),
                        seatRowEq(cond.getSeatRow()),
                        seatColEq(cond.getSeatCol()),
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
                        zoneEq(cond.getZone()),
                        seatRowEq(cond.getSeatRow()),
                        seatColEq(cond.getSeatCol()),
                        gradeEq(cond.getGrade()),
                        statusEq(cond.getStatus())
                )
                .fetchOne();

        long totalCount = (total != null) ? total : 0L;

        return new PageImpl<>(content, pageable, totalCount);
    }

    @Override
    public void update(UpdateSeatRequest info) {
        for (UpdateSeatAreaConfig config : info.getUpdateSeatAreaConfigs()) {
            Seat seat = selectById(config.getId());
            seat.update(config);
        }
    }

    @Override
    public void delete(Long id) {
        Seat seat = selectById(id);
        jpaRepository.delete(seat);
    }

    @Override
    public void deleteByIdList(List<Long> seatIdList) {
        jpaRepository.deleteBySeatIdIn(seatIdList);
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

    private BooleanExpression zoneEq(String zone) {
        return StringUtils.hasText(zone) ? seat.zone.eq(zone) : null;
    }

    private BooleanExpression seatRowEq(Integer seatRow) {
        return seatRow != null ? seat.seatRow.eq(seatRow) : null;
    }

    private BooleanExpression seatColEq(Integer seatCol) {
        return seatCol != null ? seat.seatCol.eq(seatCol) : null;
    }

    private BooleanExpression gradeEq(SeatGrade grade) {
        return grade != null ? seat.grade.eq(grade) : null;
    }

    private BooleanExpression statusEq(SeatStatus status) {
        return status != null ? seat.status.eq(status) : null;
    }

    private BooleanExpression statusNotAvailable() {
        // 예약 불가 상태: LOCK 또는 RESERVED
        return seat.status.in(SeatStatus.LOCKED, SeatStatus.RESERVED);
    }
}
