package dev.bum.ticket_service.service.queue;

import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.ticket_service.config.QueueAccessProperties;
import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.feign.queue.QueueServiceClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class QueueAccessServiceTest {

    @Mock
    private QueueServiceClient queueServiceClient;

    private QueueAccessProperties properties;
    private QueueAccessService queueAccessService;

    @BeforeEach
    void setUp() {
        properties = new QueueAccessProperties();
        properties.setEnabled(true);
        queueAccessService = new QueueAccessService(properties, queueServiceClient);
    }

    @Test
    @DisplayName("queue-service가 허용한 토큰은 통과시킨다")
    void validate_allows_when_queue_service_accepts_token() {
        given(queueServiceClient.validate(new QueueValidateRequest(1L, "user01", "queue-token")))
                .willReturn(new QueueValidateResponse(true, "OK"));

        assertThatCode(() -> queueAccessService.validate(1L, "user01", "queue-token"))
                .doesNotThrowAnyException();

        then(queueServiceClient).should().validate(new QueueValidateRequest(1L, "user01", "queue-token"));
    }

    @Test
    @DisplayName("queue-service가 거부한 토큰은 결제 흐름 진입을 차단한다")
    void validate_denies_when_queue_service_rejects_token() {
        given(queueServiceClient.validate(new QueueValidateRequest(1L, "user01", "queue-token")))
                .willReturn(new QueueValidateResponse(false, "INVALID_QUEUE_TOKEN"));

        assertThatThrownBy(() -> queueAccessService.validate(1L, "user01", "queue-token"))
                .isInstanceOf(QueueAccessDeniedException.class);
    }

    @Test
    @DisplayName("비활성화된 queue 접근 검증은 queue-service를 호출하지 않는다")
    void validate_skips_queue_service_when_disabled() {
        properties.setEnabled(false);

        assertThatCode(() -> queueAccessService.validate(1L, "user01", "queue-token"))
                .doesNotThrowAnyException();

        then(queueServiceClient).should(never()).validate(any());
    }

    @Test
    @DisplayName("active token 회수 실패는 결제 완료 흐름을 롤백하지 않도록 삼킨다")
    void complete_swallows_queue_service_failure() {
        doThrow(org.mockito.Mockito.mock(FeignException.class))
                .when(queueServiceClient)
                .complete(any());

        assertThatCode(() -> queueAccessService.complete(1L, "user01", "queue-token"))
                .doesNotThrowAnyException();

        then(queueServiceClient).should().complete(any());
    }
}
