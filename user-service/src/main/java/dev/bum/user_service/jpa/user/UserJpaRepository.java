package dev.bum.user_service.jpa.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByUserIdForUpdate(@Param("userId") String userId);
    List<User> findByIsBlacklistedTrueAndBlacklistedUntilLessThan(LocalDate today);
    Optional<User> findByNameAndPhoneNumber(String name, String phoneNumber);
    Optional<User> findByNameAndEmail(String name, String email);
    Optional<User> findByUserIdAndNameAndPhoneNumber(String userId, String name, String phoneNumber);
    Optional<User> findByUserIdAndNameAndEmail(String userId, String name, String email);
}
