package dev.bum.ticket_service.jpa.seat;

import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.common.service.ticket.seat.vo.UpdateSeatAreaConfig;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "zone", "seat_row", "seat_col"})
)
@Getter
@NoArgsConstructor
public class Seat {

    // 🌟 날짜 포맷 최적화를 위한 포맷터 상수화
    private static final DateTimeFormatter EVENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");

    // 좌석 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    // 공연 또는 발행 이벤트 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // 좌석 구역 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    // 좌석 구역.
    @Column(nullable = false, length = 50)
    private String zone;

    // 좌석 행 번호.
    @Column(name = "seat_row", nullable = false)
    private Integer seatRow;

    // 좌석 열 번호.
    @Column(name = "seat_col", nullable = false)
    private Integer seatCol;

    // 좌석 등급.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatGrade grade;

    // 가격.
    @Column(nullable = false)
    private Integer price;

    // 좌석 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SeatStatus status;

    // 배치도에서 좌석의 X 좌표.
    @Column(name = "position_x")
    private Double positionX;

    // 배치도에서 좌석의 Y 좌표.
    @Column(name = "position_y")
    private Double positionY;

    // 배치도에서 좌석 너비.
    @Column(name = "seat_width")
    private Double seatWidth;

    // 배치도에서 좌석 높이.
    @Column(name = "seat_height")
    private Double seatHeight;

    // 좌석 회전 각도.
    private Double rotation;

    // 배치도 회전 각도.
    @Column(name = "layout_angle")
    private Double layoutAngle;

    // 생성 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 계층 간 전달에 사용할 DTO로 변환한다.
    public SeatResponse toDto() {

        return SeatResponse.builder()
                .seatId(this.seatId)
                .zone(this.zone)
                .seatRow(this.seatRow)
                .seatCol(this.seatCol)
                .seatName(String.format("%s %d열 %d번", this.zone, this.seatRow, this.seatCol))
                .grade(this.grade)
                .price(this.price)
                .status(this.status)
                .positionX(this.positionX)
                .positionY(this.positionY)
                .seatWidth(this.seatWidth)
                .seatHeight(this.seatHeight)
                .rotation(this.rotation)
                .layoutAngle(this.layoutAngle)
                // 연관관계 Event 데이터 조립 (N+1 고려 필수, 서비스단 fetch join 권장)
                .eventId(this.event != null ? this.event.getEventId() : null)
                .areaId(this.area != null ? this.area.getAreaId() : null)
                .areaName(this.area != null ? this.area.getAreaName() : null)
                .artistName(this.event != null ? this.event.getArtistName() : null)
                .title(this.event != null ? this.event.getTitle() : null)
                .venue(this.event != null ? this.event.getVenue() : null)
                .eventDateTime(this.event != null && this.event.getEventDateTime() != null ?
                        this.event.getEventDateTime().format(EVENT_FORMATTER) : null)
                .build();
    }

    // 전달받은 값으로 좌석 객체를 초기화한다.
    @Builder
    public Seat(
            Long seatId,
            Event event,
            Area area,
            String zone,
            Integer seatRow,
            Integer seatCol,
            SeatGrade grade,
            Integer price,
            SeatStatus status,
            Double positionX,
            Double positionY,
            Double seatWidth,
            Double seatHeight,
            Double rotation,
            Double layoutAngle,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {

        this.seatId = seatId;
        this.area = area;
        this.zone = zone;
        this.seatRow = seatRow;
        this.seatCol = seatCol;
        this.grade = grade;
        this.price = price;
        this.status = status;
        this.positionX = positionX;
        this.positionY = positionY;
        this.seatWidth = seatWidth;
        this.seatHeight = seatHeight;
        this.rotation = rotation;
        this.layoutAngle = layoutAngle;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        if (event != null) {
            changeEvent(event);
        }
    }

    // Event 연관관계 편의 메서드
    private void changeEvent(Event event) {

        this.event = event;
        if (event.getSeats() != null) {
            event.getSeats().add(this);
        }
    }

    // 좌석을 수정한다.
    public void update(UpdateSeatAreaConfig config) {

        if (config.getPrice() != null) this.price = config.getPrice();

        if (config.getStatus() != null) this.status = config.getStatus();

        if (config.getPositionX() != null) this.positionX = config.getPositionX();

        if (config.getPositionY() != null) this.positionY = config.getPositionY();

        if (config.getSeatWidth() != null) this.seatWidth = config.getSeatWidth();

        if (config.getSeatHeight() != null) this.seatHeight = config.getSeatHeight();

        if (config.getRotation() != null) this.rotation = config.getRotation();

        if (config.getLayoutAngle() != null) this.layoutAngle = config.getLayoutAngle();
    }

    // 좌석을 예매 가능한 상태로 변경한다.
    public void available() {

        this.status = SeatStatus.AVAILABLE;
    }

    // 좌석 처리에서 동시 변경을 막기 위한 잠금을 획득한다.
    public void lock() {

        this.status = SeatStatus.LOCKED;
    }

    // 좌석을 예매 완료 상태로 변경한다.
    public void reserved() {

        this.status = SeatStatus.RESERVED;
    }
}
