package dev.bum.admin_service.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.config.KafkaDlqProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KafkaDlqQueryServiceTest {
    private final DlqMessageJpaRepository repository = mock(DlqMessageJpaRepository.class);
    private final DlqMessageHandleHistoryJpaRepository histories = mock(DlqMessageHandleHistoryJpaRepository.class);
    private final KafkaDlqProperties properties = new KafkaDlqProperties();
    private final KafkaDlqQueryService service;

    KafkaDlqQueryServiceTest() {
        properties.setMappings(Map.of("user-event.DLT", "user-event"));
        service = new KafkaDlqQueryService(properties, repository, histories, new DlqRecordCodec(new ObjectMapper()));
    }

    private DlqMessage message() {
        DlqMessage message = new DlqMessage();
        ReflectionTestUtils.setField(message, "dltTopic", "user-event.DLT");
        ReflectionTestUtils.setField(message, "messageOffset", 10L);
        ReflectionTestUtils.setField(message, "payload", "test".getBytes());
        ReflectionTestUtils.setField(message, "headersJson", "[]");
        message.changeStatus(DlqMessageStatus.PENDING);
        return message;
    }

    @Test
    void listUsesDbAndDoesNotQueryHistoryPerMessage() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(message())));
        var result = service.messages("user-event.DLT", null, null, 20);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getProcessingStatus()).isEqualTo("PENDING");
        assertThat(result.getFirst().getPayloadPreview()).isEqualTo("test");
        verifyNoInteractions(histories);
    }

    @Test
    void detailComesFromStoredBytes() {
        when(repository.findByDltTopicAndPartitionNoAndMessageOffset("user-event.DLT", 0, 10L)).thenReturn(Optional.of(message()));
        var detail = service.detail("user-event.DLT", 0, 10L);
        assertThat(detail.getPayload()).isEqualTo("test");
        assertThat(detail.getPayloadBase64()).isEqualTo("dGVzdA==");
        assertThat(detail.getProcessingStatus()).isEqualTo("PENDING");
    }

    @Test
    void topicAllowlistIsEnforced() {
        assertThatThrownBy(() -> service.messages("other", null, null, 20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void offsetRequiresPartition() {
        assertThatThrownBy(() -> service.messages("user-event.DLT", null, 1L, 20)).isInstanceOf(IllegalArgumentException.class);
    }
}
