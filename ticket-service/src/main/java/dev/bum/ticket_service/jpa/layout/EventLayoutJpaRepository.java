package dev.bum.ticket_service.jpa.layout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventLayoutJpaRepository extends JpaRepository<EventLayout, Long> {
    Optional<EventLayout> findByEvent_EventId(Long eventId);

    boolean existsByEvent_EventId(Long eventId);

    void deleteByEvent_EventId(Long eventId);
}
