package dev.bum.audit_service.kafka;

import dev.bum.audit_service.audit.AuditLogEntity;
import dev.bum.audit_service.audit.AuditLogPersistenceService;
import dev.bum.common.kafka.audit.AuditLogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class AuditLogConsumerTest {

    private final AuditLogPersistenceService persistenceService = mock(AuditLogPersistenceService.class);
    private final AuditLogConsumer consumer = new AuditLogConsumer(persistenceService);

    @Test
    @DisplayName("감사 로그 이벤트를 저장 서비스로 위임")
    void consume_saves_audit_log_event() {
        AuditLogEvent event = auditLogEvent();
        given(persistenceService.save(event)).willReturn(auditLogEntity());

        consumer.consume(event);

        then(persistenceService).should().save(event);
    }

    @Test
    @DisplayName("저장 실패 예외를 Kafka error handler로 전파")
    void consume_propagates_exception() {
        AuditLogEvent event = auditLogEvent();
        willThrow(new IllegalStateException("db error"))
                .given(persistenceService)
                .save(event);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db error");
    }

    private AuditLogEvent auditLogEvent() {
        return AuditLogEvent.builder()
                .occurredAt(LocalDateTime.now())
                .serviceName("auth-service")
                .actorType("USER")
                .actorId("user01")
                .action("LOGIN")
                .targetType("AUTH")
                .targetId("user01")
                .result("SUCCESS")
                .requestId("request-1")
                .traceId("trace-1")
                .build();
    }

    private AuditLogEntity auditLogEntity() {
        return AuditLogEntity.builder()
                .occurredAt(LocalDateTime.now())
                .serviceName("auth-service")
                .actorType("USER")
                .action("LOGIN")
                .result("SUCCESS")
                .build();
    }
}
