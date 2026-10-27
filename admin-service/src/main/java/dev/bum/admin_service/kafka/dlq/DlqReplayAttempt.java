package dev.bum.admin_service.kafka.dlq;

import lombok.Builder;

@Builder
public record DlqReplayAttempt(Long historyId, String targetTopic, byte[] key, byte[] payload, String headersJson) {}
