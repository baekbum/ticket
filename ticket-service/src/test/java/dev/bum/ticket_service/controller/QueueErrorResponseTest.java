package dev.bum.ticket_service.controller;

import dev.bum.common.error.ErrorCode;
import dev.bum.ticket_service.controller.advice.GlobalExceptionHandler;
import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.exception.queue.QueueUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueueErrorResponseTest {
    @Test
    void expiry_service_failure_and_access_denial_have_distinct_responses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var expired = handler.handleActiveTokenExpired(new ActiveTokenExpiredException());
        var unavailable = handler.handleQueueUnavailable(new QueueUnavailableException());
        var denied = handler.handleQueueAccessDeniedException(new QueueAccessDeniedException("denied"));
        assertThat(expired.getStatusCode().value()).isEqualTo(410);
        assertThat(expired.getBody().getCode()).isEqualTo(ErrorCode.ACTIVE_TOKEN_EXPIRED.name());
        assertThat(unavailable.getStatusCode().value()).isEqualTo(503);
        assertThat(unavailable.getBody().getCode()).isEqualTo(ErrorCode.QUEUE_UNAVAILABLE.name());
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }
}
