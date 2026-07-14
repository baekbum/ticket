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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "layout_id")
    private Long layoutId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "svg_text", nullable = false, columnDefinition = "TEXT")
    private String svgText;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void replace(String originalFileName, String svgText) {
        this.originalFileName = originalFileName;
        this.svgText = svgText;
    }

    public EventLayoutResponse toResponse() {
        return EventLayoutResponse.builder()
                .layoutId(this.layoutId)
                .eventId(this.event != null ? this.event.getEventId() : null)
                .originalFileName(this.originalFileName)
                .svgText(this.svgText)
                .build();
    }
}
