package dev.bum.ticket_service.jpa.area;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.InsertAreaRequest;
import dev.bum.common.service.ticket.area.dto.UpdateAreaRequest;
import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.exception.area.AreaDuplicateException;
import dev.bum.ticket_service.exception.area.AreaNotExistException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.event.EventRepository;
import dev.bum.ticket_service.jpa.event.QEvent;
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
public class AreaRepositoryImpl implements AreaRepository {

    private final JPAQueryFactory queryFactory;
    private final AreaJpaRepository jpaRepository;
    private final EventRepository eventRepository;
    private QArea area;

    @Override
    public Area insert(InsertAreaRequest info) {
        Event event = eventRepository.selectById(info.getEventId());

        AreaCondRequest cond = AreaCondRequest.builder()
                .eventId(info.getEventId())
                .areaName(info.getAreaName())
                .build();
        isExist(cond);

        return jpaRepository.save(new Area(info, event));
    }

    @Override
    public void isExist(AreaCondRequest cond) {
        area = QArea.area;

        List<Area> found = queryFactory
                .select(area)
                .from(area)
                .where(
                        eventIdEq(cond.getEventId()),
                        areaNameEq(cond.getAreaName())
                )
                .fetch();

        if (!found.isEmpty()) {
            throw new AreaDuplicateException("해당 이벤트에 동일한 구역명이 이미 존재합니다.");
        }
    }

    @Override
    public Area selectById(Long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new AreaNotExistException("해당 구역 정보가 존재하지 않습니다."));
    }

    @Override
    public Page<Area> selectByCond(AreaCondRequest cond, Pageable pageable) {
        area = QArea.area;
        QEvent event = QEvent.event;

        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();
            PathBuilder<Area> entityPath = new PathBuilder<>(Area.class, "area");
            orderSpecifiers.add(new OrderSpecifier(direction, entityPath.get(property)));
        });

        List<Area> content = queryFactory
                .selectFrom(area)
                .join(area.event, event).fetchJoin()
                .where(searchConditions(cond))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .fetch();

        Long total = queryFactory
                .select(area.count())
                .from(area)
                .join(area.event, event)
                .where(searchConditions(cond))
                .fetchOne();

        long totalCount = total != null ? total : 0L;
        return new PageImpl<>(content, pageable, totalCount);
    }

    @Override
    public Area update(Long id, UpdateAreaRequest info) {
        Area area = selectById(id);
        area.update(info);
        return area;
    }

    @Override
    public Area delete(Long id) {
        Area area = selectById(id);
        jpaRepository.delete(area);
        return area;
    }

    private BooleanExpression[] searchConditions(AreaCondRequest cond) {
        return new BooleanExpression[]{
                areaIdEq(cond.getAreaId()),
                eventIdEq(cond.getEventId()),
                areaNameLike(cond.getAreaName()),
                gradeEq(cond.getGrade()),
                statusEq(cond.getStatus())
        };
    }

    private BooleanExpression areaIdEq(Long areaId) {
        return areaId != null ? area.areaId.eq(areaId) : null;
    }

    private BooleanExpression eventIdEq(Long eventId) {
        if (eventId == null) return null;
        Event event = eventRepository.selectById(eventId);
        return area.event.eq(event);
    }

    private BooleanExpression areaNameEq(String areaName) {
        return StringUtils.hasText(areaName) ? area.areaName.eq(areaName) : null;
    }

    private BooleanExpression areaNameLike(String areaName) {
        return StringUtils.hasText(areaName) ? area.areaName.like("%" + areaName + "%") : null;
    }

    private BooleanExpression gradeEq(SeatGrade grade) {
        return grade != null ? area.grade.eq(grade) : null;
    }

    private BooleanExpression statusEq(AreaStatus status) {
        return status != null ? area.status.eq(status) : null;
    }
}
