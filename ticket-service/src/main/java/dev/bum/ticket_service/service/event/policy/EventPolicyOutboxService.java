package dev.bum.ticket_service.service.event.policy;

import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.policy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class EventPolicyOutboxService {
    private final EventPolicyOutboxJpaRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Event event, boolean deleted) {
        repository.save(EventPolicyOutbox.snapshot(event, deleted));
    }
}
