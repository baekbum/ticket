package dev.bum.ticket_service.service.event.policy;

import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.policy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class EventPolicyOutboxService {

    // 공연 정책 데이터를 조회하고 저장하는 레포지토리.
    private final EventPolicyOutboxJpaRepository repository;

    /**
     * 공연 처리에서 발행 대기 이벤트로 저장한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Event event, boolean deleted) {

        repository.save(EventPolicyOutbox.snapshot(event, deleted));
    }
}
