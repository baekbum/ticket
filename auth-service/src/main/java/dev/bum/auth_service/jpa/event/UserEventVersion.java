package dev.bum.auth_service.jpa.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 삭제 후에도 보존하여 과거 CREATE가 계정을 복원하지 못하게 한다. */
@Entity
@Table(name = "user_event_versions")
@Getter
@NoArgsConstructor
public class UserEventVersion {

    // 인증 정보 식별자.
    @Id
    @Column(name = "auth_id")
    private Long authId;

    // 마지막으로 반영한 이벤트 버전.
    @Column(name = "last_event_version", nullable = false)
    private long lastEventVersion;

    // 삭제 여부.
    @Column(nullable = false)
    private boolean deleted;

    // 이미 반영한 이벤트보다 오래된 이벤트인지 확인한다.
    public boolean isStale(long incomingVersion) {

        return deleted || incomingVersion <= lastEventVersion;
    }

    // 사용자 이벤트 버전 처리에서 이벤트 버전을 증가시키고 최신 상태를 반영한다.
    public void advance(long incomingVersion, boolean deleted) {

        this.lastEventVersion = incomingVersion;
        this.deleted = deleted;
    }
}
