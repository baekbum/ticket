package dev.bum.support_service.jpa.notice;

import dev.bum.support_service.jpa.common.PublicationStatus;
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
        name = "notices",
        indexes = {
                @Index(name = "idx_notices_public_list", columnList = "status, is_pinned, published_at"),
                @Index(name = "idx_notices_category_status", columnList = "category, status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    // 공지사항의 내부 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    // 공지 목록과 상세 화면에 표시할 제목.
    @Column(nullable = false, length = 200)
    private String title;

    // 공지 상세 화면에 표시할 본문.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 일반, 서비스, 이벤트, 점검을 구분하는 공지 분류.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NoticeCategory category;

    // 초안, 공개, 숨김, 보관을 나타내는 게시 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status;

    // 공지를 작성한 관리자 계정의 외부 식별자.
    @Column(name = "author_id", nullable = false, length = 50)
    private String authorId;

    // 일반 공지보다 먼저 노출할 중요 공지 여부.
    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    // 공지 상세 화면이 조회된 누적 횟수.
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    // 공지가 마지막으로 공개 처리된 시각.
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // 공지가 최초 생성된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 공지 내용이나 상태가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 동시에 수정할 때 갱신 충돌을 감지하기 위한 버전.
    @Version
    @Column(nullable = false)
    private long version;

    private Notice(String title, String content, NoticeCategory category, String authorId) {
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.category = Objects.requireNonNull(category);
        this.authorId = Objects.requireNonNull(authorId);
        this.status = PublicationStatus.DRAFT;
    }

    public static Notice create(String title, String content, NoticeCategory category, String authorId) {
        return new Notice(title, content, category, authorId);
    }

    public void update(String title, String content, NoticeCategory category) {
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.category = Objects.requireNonNull(category);
    }

    public void publish() {
        this.status = PublicationStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void hide() {
        this.status = PublicationStatus.HIDDEN;
    }

    public void archive() {
        this.status = PublicationStatus.ARCHIVED;
        this.pinned = false;
    }

    public void changePinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }
}
