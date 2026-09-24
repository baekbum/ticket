package dev.bum.support_service.jpa.inquiry;

import dev.bum.support_service.exception.InquiryStateConflictException;
import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "inquiries",
        indexes = {
                @Index(name = "idx_inquiries_requester_created", columnList = "requester_id, created_at"),
                @Index(name = "idx_inquiries_status_created", columnList = "status, created_at"),
                @Index(name = "idx_inquiries_category_status", columnList = "category, status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long id;

    @Column(name = "requester_id", nullable = false, length = 50)
    private String requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InquiryCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private Inquiry(String requesterId, InquiryCategory category, String title, String content) {
        this.requesterId = requireText(requesterId, "문의 작성자 정보가 필요합니다.");
        this.category = Objects.requireNonNull(category, "문의 카테고리가 필요합니다.");
        this.title = requireText(title, "문의 제목이 필요합니다.");
        this.content = requireText(content, "문의 내용이 필요합니다.");
        this.status = InquiryStatus.WAITING;
    }

    public static Inquiry create(String requesterId, InquiryCategory category, String title, String content) {
        return new Inquiry(requesterId, category, title, content);
    }

    public void update(InquiryCategory category, String title, String content) {
        if (status == InquiryStatus.ANSWERED) {
            throw new InquiryStateConflictException("답변이 완료된 문의는 수정할 수 없습니다.");
        }
        this.category = Objects.requireNonNull(category, "문의 카테고리가 필요합니다.");
        this.title = requireText(title, "문의 제목이 필요합니다.");
        this.content = requireText(content, "문의 내용이 필요합니다.");
    }

    public void markAnswered() {
        if (status == InquiryStatus.ANSWERED) {
            throw new InquiryStateConflictException("이미 답변이 등록된 문의입니다.");
        }
        status = InquiryStatus.ANSWERED;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
