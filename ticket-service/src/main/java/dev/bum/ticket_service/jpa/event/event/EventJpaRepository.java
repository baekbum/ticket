package dev.bum.ticket_service.jpa.event.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventJpaRepository extends JpaRepository<Event, Long> {

}
