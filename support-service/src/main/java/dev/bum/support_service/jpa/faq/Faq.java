package dev.bum.support_service.jpa.faq;

import dev.bum.common.service.support.notice.enums.PublicationStatus;
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
        name = "faqs",
        indexes = {
                @Index(name = "idx_faqs_public_list", columnList = "status, category, display_order"),
                @Index(name = "idx_faqs_updated_at", columnList = "updated_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq {

    // FAQ의 내부 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faq_id")
    private Long id;

    // 사용자 화면에 표시할 자주 묻는 질문.
    @Column(nullable = false, length = 300)
    private String question;

    // 질문을 펼쳤을 때 표시할 답변 본문.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    // 예매, 결제, 환불, 티켓, 계정 등을 구분하는 FAQ 분류.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FaqCategory category;

    // 초안, 공개, 숨김, 보관을 나타내는 게시 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status;

    // 같은 카테고리 안에서 FAQ를 노출할 순서. 낮을수록 먼저 노출한다.
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // FAQ를 작성한 관리자 계정의 외부 식별자.
    @Column(name = "author_id", nullable = false, length = 50)
    private String authorId;

    // FAQ가 최초 생성된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // FAQ 내용이나 상태가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 동시에 수정할 때 갱신 충돌을 감지하기 위한 버전.
    @Version
    @Column(nullable = false)
    private long version;

    private Faq(String question, String answer, FaqCategory category, int displayOrder, String authorId) {
        this.question = Objects.requireNonNull(question);
        this.answer = Objects.requireNonNull(answer);
        this.category = Objects.requireNonNull(category);
        this.displayOrder = displayOrder;
        this.authorId = Objects.requireNonNull(authorId);
        this.status = PublicationStatus.DRAFT;
    }

    public static Faq create(
            String question,
            String answer,
            FaqCategory category,
            int displayOrder,
            String authorId
    ) {
        return new Faq(question, answer, category, displayOrder, authorId);
    }

    public void update(String question, String answer, FaqCategory category, int displayOrder) {
        this.question = Objects.requireNonNull(question);
        this.answer = Objects.requireNonNull(answer);
        this.category = Objects.requireNonNull(category);
        this.displayOrder = displayOrder;
    }

    public void publish() {
        this.status = PublicationStatus.PUBLISHED;
    }

    public void hide() {
        this.status = PublicationStatus.HIDDEN;
    }

    public void archive() {
        this.status = PublicationStatus.ARCHIVED;
    }
}
