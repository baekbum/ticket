package dev.bum.user_service.jpa.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_kafka_event_states")
@Getter
@NoArgsConstructor
public class UserKafkaEventState {
    @Id
    @Column(name = "user_pk")
    private Long userPk;

    // 회원 PK를 이 상태의 PK이자 FK로 공유한다. 상태용 ID는 별도로 발급하지 않는다.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_pk", nullable = false)
    private User user;

    @Column(name = "event_version", nullable = false)
    private long eventVersion = 0L;

    @Column(name = "password_reset_version", nullable = false)
    // 이벤트 버전과 별도로, 비밀번호 찾기를 통한 재설정마다 1씩 증가한다.
    private long passwordResetVersion = 0L;

    @Builder
    private UserKafkaEventState(User user) {
        this.user = user;
    }

    public void advance(boolean passwordReset) {
        eventVersion = Math.addExact(eventVersion, 1L);
        if (passwordReset) {
            passwordResetVersion = Math.addExact(passwordResetVersion, 1L);
        }
    }
}
