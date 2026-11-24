package dev.bum.audit_service.ticketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class TicketingDetectionLogInsertRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public void insertIfAbsent(TicketingDetectionLogEntity entry) {

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", entry.getEventId())
                .addValue("userId", entry.getUserId())
                .addValue("sessionKeyHash", entry.getSessionKeyHash())
                .addValue("detectionType", entry.getDetectionType().name())
                .addValue("requestId", entry.getRequestId())
                .addValue("evidence", serializeEvidence(entry.getEvidence()))
                .addValue("occurredAt", OffsetDateTime.ofInstant(entry.getOccurredAt(), ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE);

        jdbc.update("""
                insert into ticketing_detection_log (
                    event_id, user_id, session_key_hash, detection_type, request_id, evidence, occurred_at
                ) values (
                    :eventId, :userId, :sessionKeyHash, :detectionType, :requestId, cast(:evidence as jsonb), :occurredAt
                ) on conflict (event_id, user_id, session_key_hash, detection_type) do nothing
                """, parameters);
    }

    private String serializeEvidence(Map<String, Object> evidence) {

        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("티켓팅 탐지 근거를 JSON으로 변환할 수 없습니다.");
        }
    }
}
