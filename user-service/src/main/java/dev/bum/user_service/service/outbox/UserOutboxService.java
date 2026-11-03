package dev.bum.user_service.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.user_service.jpa.outbox.UserOutboxEvent;
import dev.bum.user_service.jpa.outbox.UserOutboxEventJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserOutboxService {

    // 엔티티의 영속 상태와 데이터베이스 잠금을 관리하는 도구.
    private final EntityManager entityManager;
    // 요청과 이벤트를 JSON으로 변환하는 직렬화 도구.
    private final ObjectMapper objectMapper;
    // 사용자 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final UserOutboxEventJpaRepository repository;
    // 사용자 Outbox 이벤트 발행 대기 작업을 순회하고 실행하는 도구.
    private final UserOutboxDispatcher dispatcher;
    // 사용자 변경 이벤트를 발행할 Kafka 토픽.
    @Value("${topic.user.name}")
    private String userTopic;

    /**
     * 사용자 Outbox 이벤트 처리에서 발행 대기 이벤트로 저장한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UserDtoForEvent event) {

        if (!StringUtils.hasText(event.getIdempotencyKey())) {
            event.setIdempotencyKey(UUID.randomUUID().toString());
        }

        String payload;

        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 이벤트를 기록하지 못하면 회원 변경도 롤백한다. 민감한 payload는 예외에 넣지 않는다.
            throw new IllegalStateException("회원 이벤트를 저장할 수 없습니다.");
        }

        // 회원 행 변경을 먼저 실행하여 같은 회원의 Outbox ID 순서가 DB 변경 순서를 따르게 한다.
        // flush만 수행하므로 회원 변경과 Outbox 저장은 여전히 함께 커밋·롤백된다.
        entityManager.flush();

        UserOutboxEvent saved = repository.save(UserOutboxEvent.builder()
                .userId(event.getUserId())
                .topic(userTopic)
                .payload(payload)
                .nextAttemptAt(LocalDateTime.now())
                .build());

        Long outboxId = saved.getOutboxId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * 트랜잭션 커밋이 완료된 뒤 등록한 후속 작업을 실행한다.
             */
            @Override
            public void afterCommit() {

                // 즉시 발행 실패나 서버 종료가 있어도 커밋된 PENDING 기록을 스케줄러가 다시 처리한다.
                dispatcher.publishSafely(outboxId);
            }
        });
    }
}
