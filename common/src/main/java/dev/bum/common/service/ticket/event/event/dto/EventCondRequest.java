package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventCondRequest {

    // 공연 식별자.
    private Long eventId;

    // 공연 아티스트 이름.
    private String artistName;

    // 제목.
    private String title;

    // 같은 공연의 회차를 묶는 그룹 코드.
    private String eventGroupCode;

    // 공연장 이름.
    private String venue;

    // 공연장 주소.
    private String venueAddress;

    // 공연 포스터 URL.
    private String posterUrl;

    // 공연일.
    private LocalDate eventDate;

    // 공연일 조회 시작 조건.
    private LocalDate eventDateFrom;

    // 공연일 조회 종료 조건.
    private LocalDate eventDateTo;

    // 판매 시작일.
    private LocalDate saleStartDate;

    // 판매 종료일.
    private LocalDate saleEndDate;

    // 판매 시작일 조회 시작 조건.
    private LocalDate saleStartDateFrom;

    // 판매 시작일 조회 종료 조건.
    private LocalDate saleStartDateTo;

    // 판매 종료일 조회 시작 조건.
    private LocalDate saleEndDateFrom;

    // 판매 종료일 조회 종료 조건.
    private LocalDate saleEndDateTo;

    // 취소 마감일.
    private LocalDate cancelDeadlineDate;

    // 취소 마감일 조회 시작 조건.
    private LocalDate cancelDeadlineDateFrom;

    // 취소 마감일 조회 종료 조건.
    private LocalDate cancelDeadlineDateTo;

    // 공연 진행 시간(분).
    private Integer runningMinutes;

    // 관람 가능 최소 연령.
    private Integer ageLimit;

    // 전체 좌석 수.
    private Integer totalSeats;

    // 예매 가능한 좌석 수.
    private Integer availableSeats;

    // 공연 상태.
    private EventStatus status;

    // 구매 수량 제한 적용 범위.
    private TicketLimitScope ticketLimitScope;

    // 공연 장르.
    private EventGenre genre;

    // 공연 지역.
    private EventRegion region;

    // 공연 테마.
    private EventTheme theme;

    // 조회할 페이지.
    @Builder.Default // 빌더 패턴을 사용해서 만들 때도 기본값을 유지
    private Integer page = 0; // page 필드에 기본값 0 할당

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10; // size 필드에 기본값 10 할당

    // 조회 결과 정렬 조건.
    List<String> sort; // "sort": ["teamName-asc","createdAt-desc"] 예시

    // 객체의 문자열 표현을 반환한다.
    @Override
    public String toString() {

        // 🌟 콤마(,) 조립을 편하게 하기 위해 StringJoiner 활용 (EventCond 형태로 시작)
        StringJoiner sj = new StringJoiner(", ", "EventCond{", "}");

        // 필수 필드 (기본값이 항상 존재하므로 무조건 포함)
        sj.add("page=" + page);
        sj.add("size=" + size);

        // 검색 조건 필드 검증 (타입에 맞춰 문자열은 작은따옴표 처리)
        if (eventId != null) sj.add("eventId=" + eventId);

        if (artistName != null) sj.add("artistName='" + artistName + "'");

        if (title != null) sj.add("title='" + title + "'");

        if (eventGroupCode != null) sj.add("eventGroupCode='" + eventGroupCode + "'");

        if (venue != null) sj.add("venue='" + venue + "'");

        if (venueAddress != null) sj.add("venueAddress='" + venueAddress + "'");

        if (posterUrl != null) sj.add("posterUrl='" + posterUrl + "'");

        if (eventDate != null) sj.add("eventDate=" + eventDate);

        if (eventDateFrom != null) sj.add("eventDateFrom=" + eventDateFrom);

        if (eventDateTo != null) sj.add("eventDateTo=" + eventDateTo);

        if (saleStartDate != null) sj.add("saleStartDate=" + saleStartDate);

        if (saleStartDateFrom != null) sj.add("saleStartDateFrom=" + saleStartDateFrom);

        if (saleStartDateTo != null) sj.add("saleStartDateTo=" + saleStartDateTo);

        if (saleEndDate != null) sj.add("saleEndDate=" + saleEndDate);

        if (saleEndDateFrom != null) sj.add("saleEndDateFrom=" + saleEndDateFrom);

        if (saleEndDateTo != null) sj.add("saleEndDateTo=" + saleEndDateTo);

        if (cancelDeadlineDate != null) sj.add("cancelDeadlineDate=" + cancelDeadlineDate);

        if (cancelDeadlineDateFrom != null) sj.add("cancelDeadlineDateFrom=" + cancelDeadlineDateFrom);

        if (cancelDeadlineDateTo != null) sj.add("cancelDeadlineDateTo=" + cancelDeadlineDateTo);

        if (runningMinutes != null) sj.add("runningMinutes=" + runningMinutes);

        if (ageLimit != null) sj.add("ageLimit=" + ageLimit);

        if (totalSeats != null) sj.add("totalSeats=" + totalSeats);

        if (availableSeats != null) sj.add("availableSeats=" + availableSeats);

        if (status != null) sj.add("status=" + status);

        if (ticketLimitScope != null) sj.add("ticketLimitScope=" + ticketLimitScope);

        // 정렬 조건 필드 검증 (List 포맷팅 적용)
        if (sort != null && !sort.isEmpty()) {
            sj.add("sort=[" + String.join(", ", sort) + "]");
        }

        return sj.toString();
    }
}
