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
    @Id
    @Column(name = "auth_id")
    private Long authId;

    @Column(name = "last_event_version", nullable = false)
    private long lastEventVersion;

    @Column(nullable = false)
    private boolean deleted;

    public boolean isStale(long incomingVersion) {
        return deleted || incomingVersion <= lastEventVersion;
    }

    public void advance(long incomingVersion, boolean deleted) {
        this.lastEventVersion = incomingVersion;
        this.deleted = deleted;
    }
}
