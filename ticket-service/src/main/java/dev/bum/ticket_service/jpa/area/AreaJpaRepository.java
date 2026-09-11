package dev.bum.ticket_service.jpa.area;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AreaJpaRepository extends JpaRepository<Area, Long> {
    boolean existsByEvent_EventId(Long eventId);

    void deleteByEvent_EventId(Long eventId);

    Optional<Area> findByEvent_EventIdAndLayoutKey(Long eventId, String layoutKey);

    List<Area> findByEvent_EventIdIn(List<Long> eventIds);
}
