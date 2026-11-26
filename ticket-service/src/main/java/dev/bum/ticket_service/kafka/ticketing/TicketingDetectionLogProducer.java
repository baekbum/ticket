package dev.bum.ticket_service.kafka.ticketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.ticket_service.service.queue.BookingSessionKey;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class TicketingDetectionLogProducer {

    private static final int MAX_RETRIES = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;
    private final TaskScheduler scheduler;
    private final String topic;
    private final Optional<Tracer> tracer;

    public TicketingDetectionLogProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor,
            @Qualifier("taskScheduler") TaskScheduler scheduler,
            @Value("${topic.ticketing.detection.log.name:ticketing-detection-log}") String topic,
            Optional<Tracer> tracer
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.scheduler = scheduler;
        this.topic = topic;
        this.tracer = tracer;
    }

    public void report(Long eventId, String userId, String activeToken, TicketingDetectionType detectionType,
                       Instant occurredAt, Map<String, Object> evidence) {
        send(TicketingDetectionLogEvent.builder()
                .eventId(eventId)
                .userId(userId)
                .sessionKeyHash(BookingSessionKey.hash(eventId, userId, activeToken))
                .detectionType(detectionType)
                .traceId(tracer.map(Tracer::currentSpan).map(Span::context).map(TraceContext::traceId).orElse(null))
                .occurredAt(occurredAt)
                .evidence(evidence)
                .build());
    }

    public void send(TicketingDetectionLogEvent event) {

        submit(event, () -> {
            String payload;

            try {
                payload = objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException | RuntimeException e) {
                logFailure(event, e);
                return;
            }

            publish(event, payload, 0);
        });
    }

    private void submit(TicketingDetectionLogEvent event, Runnable task) {

        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            logFailure(event, e);
        }
    }

    private void publish(TicketingDetectionLogEvent event, String payload, int retryCount) {

        try {
            kafkaTemplate.send(topic, event.getSessionKeyHash(), payload)
                    .whenComplete((result, error) -> {
                        if (error != null) retryOrLog(event, payload, retryCount, error);
                    });
        } catch (RuntimeException e) {
            retryOrLog(event, payload, retryCount, e);
        }
    }

    private void retryOrLog(TicketingDetectionLogEvent event, String payload, int retryCount, Throwable error) {

        if (retryCount >= MAX_RETRIES || !isRetryable(error)) {
            logFailure(event, error);
            return;
        }

        try {
            scheduler.schedule(() -> submit(event, () -> publish(event, payload, retryCount + 1)),
                    Instant.now().plusSeconds(1));
        } catch (RejectedExecutionException e) {
            logFailure(event, e);
        }
    }

    private boolean isRetryable(Throwable error) {

        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RetriableException) return true;
        }
        return false;
    }

    private void logFailure(TicketingDetectionLogEvent event, Throwable error) {

        log.warn("티켓팅 탐지 이벤트 발행 실패: eventId={}, detectionType={}, errorType={}",
                event.getEventId(), event.getDetectionType(), error.getClass().getSimpleName());
    }
}
