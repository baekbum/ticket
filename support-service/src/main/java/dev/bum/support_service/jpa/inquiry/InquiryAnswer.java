package dev.bum.support_service.jpa.inquiry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "inquiry_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryAnswer {

    // 고유 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_answer_id")
    private Long id;

    // 문의 정보.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false, unique = true)
    private Inquiry inquiry;

    // 답변 작성자 ID.
    @Column(name = "responder_id", nullable = false, length = 50)
    private String responderId;

    // 본문.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 생성 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 동시성 제어 버전.
    @Version
    @Column(nullable = false)
    private long version;

    // 전달받은 값으로 문의 답변 객체를 초기화한다.
    private InquiryAnswer(Inquiry inquiry, String responderId, String content) {

        this.inquiry = Objects.requireNonNull(inquiry, "문의 정보가 필요합니다.");
        this.responderId = requireText(responderId, "답변자 정보가 필요합니다.");
        this.content = requireText(content, "답변 내용이 필요합니다.");
    }

    // 문의 답변을 생성한다.
    public static InquiryAnswer create(Inquiry inquiry, String responderId, String content) {

        return new InquiryAnswer(inquiry, responderId, content);
    }

    // 문의 답변을 수정한다.
    public void update(String content) {

        this.content = requireText(content, "답변 내용이 필요합니다.");
    }

    // 필수 문자열 값이 존재하는지 확인한다.
    private static String requireText(String value, String message) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
