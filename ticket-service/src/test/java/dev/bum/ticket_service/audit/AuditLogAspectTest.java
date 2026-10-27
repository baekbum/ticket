package dev.bum.ticket_service.audit;

import dev.bum.common.kafka.audit.AuditLogEvent;
import dev.bum.common.kafka.audit.AuditLogProducer;
import dev.bum.ticket_service.service.event.event.EventManagementService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuditLogAspectTest {
    @AfterEach
    void clean() {
        AuditContext.clear();
    }

    @Test
    void separateOperationsReceiveDifferentKeys() throws Throwable {
        AuditLogProducer producer = mock(AuditLogProducer.class);
        AuditLogAspect aspect = new AuditLogAspect(producer);
        ProceedingJoinPoint joinPoint = joinPoint();
        when(joinPoint.proceed()).thenReturn("ok");
        AuditLog annotation = annotation();

        aspect.writeAuditLog(joinPoint, annotation);
        aspect.writeAuditLog(joinPoint, annotation);

        ArgumentCaptor<AuditLogEvent> events = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(producer, times(2)).send(events.capture());
        assertThat(events.getAllValues()).allSatisfy(this::assertDatedKey)
                .extracting(AuditLogEvent::getIdempotencyKey).doesNotHaveDuplicates();
    }

    @Test
    void failedOperationAlsoReceivesAKeyAndKeepsOriginalException() throws Throwable {
        AuditLogProducer producer = mock(AuditLogProducer.class);
        AuditLogAspect aspect = new AuditLogAspect(producer);
        ProceedingJoinPoint joinPoint = joinPoint();
        IllegalStateException failure = new IllegalStateException("실패");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.writeAuditLog(joinPoint, annotation())).isSameAs(failure);

        ArgumentCaptor<AuditLogEvent> event = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(producer).send(event.capture());
        assertDatedKey(event.getValue());
        assertThat(event.getValue().getResult()).isEqualTo("FAILURE");
    }

    private void assertDatedKey(AuditLogEvent event) {
        assertThat(event.getIdempotencyKey()).hasSize(45)
                .startsWith(event.getOccurredAt().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) + "-");
    }

    private ProceedingJoinPoint joinPoint() throws NoSuchMethodException {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringType()).thenReturn(EventManagementService.class);
        when(signature.getMethod()).thenReturn(EventManagementService.class.getMethod("selectById", Long.class));
        return joinPoint;
    }

    private AuditLog annotation() {
        AuditLog annotation = mock(AuditLog.class);
        when(annotation.action()).thenReturn("EVENT_UPDATE");
        when(annotation.targetType()).thenReturn("EVENT");
        return annotation;
    }
}
