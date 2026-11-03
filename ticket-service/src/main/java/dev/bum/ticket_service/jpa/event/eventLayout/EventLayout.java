package dev.bum.ticket_service.jpa.event.eventLayout;

import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.ticket_service.jpa.event.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_layouts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventLayout {

    // 좌석 배치도 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "layout_id")
    private Long layoutId;

    // 공연 또는 발행 이벤트 정보.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    // 업로드한 원본 파일 이름.
    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    // SVG 좌석 배치도 내용.
    @Column(name = "svg_text", nullable = false, columnDefinition = "TEXT")
    private String svgText;

    // 생성 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 기존 설정을 전달받은 값으로 교체한다.
    public void replace(String originalFileName, String svgText) {

        this.originalFileName = originalFileName;
        this.svgText = svgText;
    }

    // 외부 응답에 사용할 DTO로 변환한다.
    public EventLayoutResponse toResponse() {

        return EventLayoutResponse.builder()
                .layoutId(this.layoutId)
                .eventId(this.event != null ? this.event.getEventId() : null)
                .originalFileName(this.originalFileName)
                .svgText(this.svgText)
                .build();
    }
}
