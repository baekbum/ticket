package dev.bum.ticket_service.jpa.event;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.exception.event.EventDuplicateException;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.vo.event.EventCond;
import dev.bum.ticket_service.vo.event.InsertEventInfo;
import dev.bum.ticket_service.vo.event.UpdateEventInfo;
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

@Slf4j
@Repository
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepository {

    private final JPAQueryFactory queryFactory;
    private final EventJpaRepository jpaRepository;
    private QEvent event;

    @Override
    public Event insert(InsertEventInfo info) {
        EventCond cond = EventCond.builder()
                .artistName(info.getArtistName())
                .title(info.getTitle())
                .venue(info.getVenue())
                .eventDate(info.getEventDateTime().toLocalDate())
                .status(EventStatus.ON_SALE)
                .build();

        // 공연 정보 중복 확인.
        isExist(cond);

        return jpaRepository.save(new Event(info));
    }

    @Override
    public void isExist(EventCond cond) {
        event = QEvent.event;

        List<Event> content = queryFactory
                .select(event)
                .from(event)
                .where(
                        artistNameLike(cond.getArtistName()),
                        titleLike(cond.getTitle()),
                        venueLike(cond.getVenue()),
                        eventDateLike(cond.getEventDate()),
                        statusEq(cond.getStatus())
                )
                .fetch();

        if (!content.isEmpty()) {
            throw new EventDuplicateException("동일한 공연 정보가 이미 존재합니다.");
        }
    }

    @Override
    public Event selectById(Long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new EventNotExistException("해당 이벤트 정보는 존재하지 않습니다."));
    }

    @Override
    public Page<Event> selectByCond(EventCond cond, Pageable pageable) {
        event = QEvent.event;

        // 1. Pageable 객체에서 Sort 정보를 추출하여 OrderSpecifier 리스트를 생성
        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();
            PathBuilder<Event> entityPath = new PathBuilder<>(Event.class, "event");
            orderSpecifiers.add(new OrderSpecifier(direction, entityPath.get(property)));
        });

        List<Event> content = queryFactory
                .select(event)
                .from(event)
                .where(
                        eventIdEq(cond.getEventId()),
                        artistNameLike(cond.getArtistName()),
                        titleLike(cond.getTitle()),
                        venueLike(cond.getVenue()),
                        eventDateLike(cond.getEventDate()),
                        statusEq(cond.getStatus())
                )
                .offset(pageable.getOffset()) // 오프셋 적용
                .limit(pageable.getPageSize()) // 페이지 크기 적용
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0])) // 정렬 정보 적용
                .fetch();

        Long total = queryFactory
                .select(event.count())
                .from(event)
                .where(
                        eventIdEq(cond.getEventId()),
                        artistNameLike(cond.getArtistName()),
                        titleLike(cond.getTitle()),
                        venueLike(cond.getVenue()),
                        eventDateLike(cond.getEventDate()),
                        statusEq(cond.getStatus())
                )
                .fetchOne();

        long totalCount = (total != null) ? total : 0L;

        return new PageImpl<>(content, pageable, totalCount);
    }

    @Override
    public Event update(Long id, UpdateEventInfo info) {
        Event event = selectById(id);
        event.update(info);

        return event;
    }

    @Override
    public Event delete(Long id) {
        Event event = selectById(id);
        jpaRepository.delete(event);

        return event;
    }

    // QueryDsl 동적 쿼리 관련 메서드
    private BooleanExpression eventIdEq(Long eventId) {
        return eventId != null ? event.eventId.eq(eventId) : null;
    }

    private BooleanExpression artistNameLike(String artistName) {
        return StringUtils.hasText(artistName) ? event.artistName.like("%" + artistName + "%") : null;
    }

    private BooleanExpression titleLike(String title) {
        return StringUtils.hasText(title) ? event.title.like("%" + title + "%") : null;
    }

    private BooleanExpression venueLike(String venue) {
        return StringUtils.hasText(venue) ? event.venue.like("%" + venue + "%") : null;
    }

    private BooleanExpression eventDateLike(LocalDate eventDate) {
        if (eventDate == null) {
            return null;
        }

        // 1. 사용자가 입력한 날짜의 시작 시간 (2026-04-29 00:00:00)
        LocalDateTime startOfDay = eventDate.atStartOfDay();

        // 2. 사용자가 입력한 날짜의 끝 시간 (2026-04-29 23:59:59.999999)
        LocalDateTime endOfDay = eventDate.atTime(LocalTime.MAX);

        // 3. DB의 eventDate가 이 범위 사이에 있는지 확인
        return event.eventDateTime.between(startOfDay, endOfDay);
    }

    private BooleanExpression statusEq(EventStatus status) {
        return status != null ? event.status.eq(status) : null;
    }
}
