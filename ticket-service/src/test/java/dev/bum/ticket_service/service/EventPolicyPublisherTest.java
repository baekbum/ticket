package dev.bum.ticket_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.event.EventQueuePolicyEvent;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.policy.*;
import dev.bum.ticket_service.service.event.policy.EventPolicyPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventPolicyPublisherTest {
    @SuppressWarnings("unchecked")
    @Test void lost_broker_response_keeps_the_same_snapshot_for_retry() throws Exception {
        EventPolicyOutboxJpaRepository repository = mock(EventPolicyOutboxJpaRepository.class);
        KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        EventPolicyPublisher publisher = new EventPolicyPublisher(repository, kafka, mapper);
        ReflectionTestUtils.setField(publisher, "topic", "event-queue-policy");
        Event event = Event.builder().eventId(1L)
                .status(dev.bum.common.service.ticket.event.event.enums.EventStatus.ON_SALE)
                .saleStartAt(LocalDateTime.of(2026, 10, 29, 0, 0))
                .saleEndAt(LocalDateTime.of(2026, 10, 30, 23, 59, 59))
                .eventDateTime(LocalDateTime.of(2026, 10, 31, 18, 0)).build();
        EventPolicyOutbox row = EventPolicyOutbox.snapshot(event, false);
        ReflectionTestUtils.setField(row, "outboxId", 10L);
        when(repository.findForUpdate(10L)).thenReturn(Optional.of(row));
        when(kafka.send(eq("event-queue-policy"), eq("1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher.deliver(10L, false);
        assertThat(row.isPublished()).isFalse();
        assertThat(row.getRetryCount()).isEqualTo(1);
        publisher.deliver(10L, false);
        assertThat(row.isPublished()).isTrue();
        ArgumentCaptor<String> json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq("event-queue-policy"), eq("1"), json.capture());
        assertThat(json.getAllValues().getFirst()).isEqualTo(json.getAllValues().getLast());
        assertThat(mapper.readValue(json.getValue(), EventQueuePolicyEvent.class).version()).isEqualTo(10L);
        publisher.deliver(10L, false);
        verifyNoMoreInteractions(kafka);
        publisher.deliver(10L, true);
        verify(kafka, times(3)).send(eq("event-queue-policy"), eq("1"), anyString());
    }
}
