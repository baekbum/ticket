package dev.bum.ticket_service.kafka.ticketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TicketingDetectionLogProducerTest {

    private static final String TOPIC = "ticketing-detection-log";
    private static final String SESSION_HASH = "a".repeat(64);

    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private TaskScheduler scheduler;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    private final Queue<Runnable> scheduledRetries = new ArrayDeque<>();
    private final TicketingDetectionLogEvent event = TicketingDetectionLogEvent.builder()
            .eventId(1L)
            .sessionKeyHash(SESSION_HASH)
            .detectionType(TicketingDetectionType.CAPTCHA_ATTEMPT_LIMIT_REACHED)
            .build();

    private TicketingDetectionLogProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TicketingDetectionLogProducer(kafkaTemplate, objectMapper, new SyncTaskExecutor(), scheduler, TOPIC);
    }

    @Test
    void synchronousAndAsynchronousFailuresShareThreeRetriesAndReusePayload() throws Exception {
        captureScheduledRetries();
        TimeoutException temporaryFailure = new TimeoutException("temporary failure");
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        given(kafkaTemplate.send(eq(TOPIC), eq(SESSION_HASH), anyString()))
                .willThrow(temporaryFailure)
                .willReturn(pending)
                .willThrow(temporaryFailure)
                .willReturn(CompletableFuture.failedFuture(temporaryFailure));

        producer.send(event);
        scheduledRetries.remove().run();
        assertThat(scheduledRetries).isEmpty();
        pending.completeExceptionally(new CompletionException(temporaryFailure));
        scheduledRetries.remove().run();
        scheduledRetries.remove().run();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(4)).send(eq(TOPIC), eq(SESSION_HASH), payload.capture());
        assertThat(payload.getAllValues()).containsOnly(payload.getValue());
        verify(objectMapper).writeValueAsString(event);
        verify(scheduler, times(3)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(scheduledRetries).isEmpty();
    }

    @Test
    void successEndsTheRetrySequence() {
        captureScheduledRetries();
        given(kafkaTemplate.send(eq(TOPIC), eq(SESSION_HASH), anyString()))
                .willReturn(CompletableFuture.failedFuture(new TimeoutException("temporary failure")))
                .willReturn(CompletableFuture.completedFuture(null));

        producer.send(event);
        scheduledRetries.remove().run();

        verify(kafkaTemplate, times(2)).send(eq(TOPIC), eq(SESSION_HASH), anyString());
        assertThat(scheduledRetries).isEmpty();
    }

    @Test
    void permanentFailureDoesNotScheduleARetry() {
        given(kafkaTemplate.send(eq(TOPIC), eq(SESSION_HASH), anyString()))
                .willReturn(CompletableFuture.failedFuture(new TopicAuthorizationException(TOPIC)));

        producer.send(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(SESSION_HASH), anyString());
        verifyNoInteractions(scheduler);
    }

    private void captureScheduledRetries() {
        given(scheduler.schedule(any(Runnable.class), any(Instant.class))).willAnswer(invocation -> {
            scheduledRetries.add(invocation.getArgument(0, Runnable.class));
            return null;
        });
    }
}
