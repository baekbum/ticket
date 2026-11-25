package dev.bum.audit_service.ticketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketingDetectionLogPersistenceService {

    private final TicketingDetectionLogJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(TicketingDetectionLogEvent event) {

        TicketingDetectionLogEntity entry = TicketingDetectionLogEntity.from(event);
        repository.insertIfAbsent(
                entry.getEventId(),
                entry.getUserId(),
                entry.getSessionKeyHash(),
                entry.getDetectionType().name(),
                entry.getTraceId(),
                serializeEvidence(entry.getEvidence()),
                OffsetDateTime.ofInstant(entry.getOccurredAt(), ZoneOffset.UTC)
        );
    }

    private String serializeEvidence(Map<String, Object> evidence) {

        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("티켓팅 탐지 근거를 JSON으로 변환할 수 없습니다.");
        }
    }
}
