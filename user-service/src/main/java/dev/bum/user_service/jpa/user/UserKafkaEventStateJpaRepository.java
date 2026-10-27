package dev.bum.user_service.jpa.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserKafkaEventStateJpaRepository extends JpaRepository<UserKafkaEventState, Long> {
}
