package dev.bum.auth_service.jpa.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserEventVersionJpaRepository extends JpaRepository<UserEventVersion, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into user_event_versions (auth_id, last_event_version, deleted)
            values (:authId, 0, false)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("authId") Long authId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from UserEventVersion v where v.authId = :authId")
    Optional<UserEventVersion> findByAuthIdForUpdate(@Param("authId") Long authId);
}
