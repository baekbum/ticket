package dev.bum.ticket_service.service.event.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.EventBookingDetailResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCardResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.dto.EventScheduleResponse;
import dev.bum.common.service.ticket.event.event.dto.EventSeatPriceResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    private static final int SOONEST_ON_SALE_EVENT_LIMIT = 10;
    private static final int HOME_TAB_EVENT_LIMIT = 4;
    private static final DateTimeFormatter BOOKING_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final EventRepository repository;
    private final AreaJpaRepository areaJpaRepository;

    /**
     * 판매 중인 공연만 사용자 화면에 노출되도록 ID로 조회한다.
     */
    @Transactional(readOnly = true)
    public EventResponse selectVisibleById(Long id) {
        Event event = repository.selectById(id);
        if (event.getStatus() != EventStatus.ON_SALE) {
            throw new EventNotExistException("노출 가능한 이벤트 정보가 존재하지 않습니다.");
        }
        return event.toResponse();
    }

    @Transactional(readOnly = true)
    public EventBookingDetailResponse selectBookingDetailByEventGroupCode(String eventGroupCode) {
        LocalDateTime now = LocalDateTime.now();
        List<Event> events = repository.selectByEventGroupCode(eventGroupCode).stream()
                .sorted(Comparator.comparing(Event::getEventDateTime))
                .toList();
        Event representativeEvent = events.get(0);
        EventResponse representativeResponse = representativeEvent.toResponse();
        List<Long> eventIds = events.stream()
                .map(Event::getEventId)
                .toList();
        Map<Long, List<Area>> areasByEventId = areaJpaRepository.findByEvent_EventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(area -> area.getEvent().getEventId()));
        LocalDateTime eventStartDateTime = events.get(0).getEventDateTime();
        LocalDateTime eventEndDateTime = events.get(events.size() - 1).getEventDateTime();
        LocalDateTime saleStartAt = events.stream()
                .map(Event::getSaleStartAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        boolean isClosed = events.stream()
                .allMatch(event -> event.getStatus() == EventStatus.CLOSED || event.getEventDateTime().isBefore(now));
        boolean isOnSale = events.stream()
                .anyMatch(event ->
                        event.getStatus() == EventStatus.ON_SALE &&
                                event.getSaleStartAt() != null &&
                                event.getSaleEndAt() != null &&
                                !event.getSaleStartAt().isAfter(now) &&
                                !event.getSaleEndAt().isBefore(now) &&
                                !event.getEventDateTime().isBefore(now)
                );
        String bookingStatus = "CLOSED";
        String bookingMessage = "종료된 공연입니다.";

        if (isOnSale) {
            bookingStatus = "ON_SALE";
            bookingMessage = "예매하기";
        } else if (!isClosed && saleStartAt != null && saleStartAt.isAfter(now)) {
            bookingStatus = "OPEN_SOON";
            bookingMessage = saleStartAt.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분")) + " 오픈 예정";
        }

        return EventBookingDetailResponse.builder()
                .eventGroupCode(representativeEvent.getEventGroupCode())
                .artistName(representativeEvent.getArtistName())
                .title(representativeEvent.getTitle())
                .description(representativeEvent.getDescription())
                .venue(representativeEvent.getVenue())
                .venueAddress(representativeEvent.getVenueAddress())
                .posterUrl(representativeEvent.getPosterUrl())
                .eventDateRange(formatEventDateRange(eventStartDateTime, eventEndDateTime))
                .saleStartAt(representativeResponse.getSaleStartAt())
                .saleEndAt(representativeResponse.getSaleEndAt())
                .runningMinutes(representativeEvent.getRunningMinutes())
                .ageLimit(representativeEvent.getAgeLimit())
                .totalSeats(events.stream().map(Event::getTotalSeats).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .availableSeats(events.stream().map(Event::getAvailableSeats).filter(Objects::nonNull).mapToInt(Integer::intValue).sum())
                .status(representativeEvent.getStatus())
                .maxTicketsPerPerson(representativeEvent.getMaxTicketsPerPerson())
                .ticketLimitScope(representativeEvent.getTicketLimitScope())
                .bookingStatus(bookingStatus)
                .bookingMessage(bookingMessage)
                .schedules(events.stream()
                        .map(event -> EventScheduleResponse.builder()
                                .eventId(event.getEventId())
                                .eventDateTime(event.toResponse().getEventDateTime())
                                .availableSeats(event.getAvailableSeats())
                                .status(event.getStatus())
                                .seatPrices(toSeatPrices(areasByEventId.getOrDefault(event.getEventId(), List.of())))
                                .build())
                        .toList())
                .build();
    }

    private List<EventSeatPriceResponse> toSeatPrices(List<Area> areas) {
        Map<SeatGrade, Integer> priceByGrade = areas.stream()
                .filter(area -> area.getGrade() != null && area.getPrice() != null)
                .collect(Collectors.toMap(
                        Area::getGrade,
                        Area::getPrice,
                        Integer::min
                ));

        return priceByGrade.entrySet().stream()
                .sorted(Map.Entry.<SeatGrade, Integer>comparingByValue().reversed())
                .map(entry -> EventSeatPriceResponse.builder()
                        .grade(entry.getKey())
                        .price(entry.getValue())
                        .build())
                .toList();
    }

    /**
     * 판매 중인 공연만 사용자 화면에 노출되도록 조건 검색한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<EventResponse> selectVisibleByCond(EventCondRequest cond) {
        cond.setStatus(EventStatus.ON_SALE);
        log.info("[SELECT VISIBLE] Info : {}", cond);
        PageRequest pageRequest = PageRequest.of(cond.getPage(), cond.getSize(), makeSortInfo(cond.getSort()));
        Page<EventResponse> eventPage = repository.selectByCond(cond, pageRequest).map(Event::toResponse);

        return CustomPageResponse.of(
                eventPage.getContent(),
                eventPage.getSize(),
                eventPage.getNumber(),
                eventPage.getTotalElements(),
                eventPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<EventCardResponse> selectSoonestOnSaleCards() {
        LocalDateTime now = LocalDateTime.now();

        return repository.selectSoonestOnSaleCards(now, SOONEST_ON_SALE_EVENT_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<EventCardResponse> selectFestivalCards() {
        LocalDateTime now = LocalDateTime.now();

        return repository.selectFestivalCards(now, HOME_TAB_EVENT_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<EventCardResponse> selectOpenSoonCards() {
        LocalDateTime now = LocalDateTime.now();

        return repository.selectOpenSoonCards(now, now.plusDays(10), HOME_TAB_EVENT_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<EventCardResponse> selectWeeklyRecommendedCards() {
        LocalDateTime now = LocalDateTime.now();

        return repository.selectWeeklyRecommendedCards(now, now.plusDays(14), HOME_TAB_EVENT_LIMIT);
    }

    /**
     * 검색 조건에서 sort 옵션을 처리하기 위한 메서드
     * @param sorts
     * @return
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

    private String formatEventDateRange(LocalDateTime start, LocalDateTime end) {
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return start.format(BOOKING_DATE_FORMATTER);
        }

        return start.format(BOOKING_DATE_FORMATTER) + " ~ " + end.format(BOOKING_DATE_FORMATTER);
    }

}
