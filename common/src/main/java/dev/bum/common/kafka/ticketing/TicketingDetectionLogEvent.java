package dev.bum.common.kafka.ticketing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketingDetectionLogEvent {

    private Long eventId;
    private String userId;
    private String sessionKeyHash;
    private TicketingDetectionType detectionType;
    private String traceId;
    private Instant occurredAt;
    private Map<String, Object> evidence;
}
