package dev.bum.admin_service.kafka.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.config.KafkaDlqProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SuppressWarnings("unchecked")
class KafkaDlqReplayServiceTest {
    private final KafkaTemplate<byte[], byte[]> kafka = mock(KafkaTemplate.class);
    private final DlqReplayTransactionService transactions = mock(DlqReplayTransactionService.class);
    private final DlqMessageHandleRequest request = new DlqMessageHandleRequest("user-event.DLT", 0, 10L, "admin", "복구");
    private KafkaDlqReplayService service;

    @BeforeEach
    void setup() {
        KafkaDlqProperties properties = new KafkaDlqProperties();
        properties.setMappings(Map.of("user-event.DLT", "user-event"));
        service = new KafkaDlqReplayService(properties, kafka, transactions, new DlqRecordCodec(new ObjectMapper()), new ObjectMapper());
        when(transactions.begin(any(), any(), any(), any())).thenReturn(DlqReplayAttempt.builder()
                .historyId(1L).targetTopic("user-event").key(new byte[]{1}).payload(new byte[]{2})
                .headersJson("[{\"key\":\"trace\",\"value\":\"AQ==\"},{\"key\":\"kafka_dlt-exception-message\",\"value\":null}]")
                .build());
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void commitsClaimBeforeSendingAndCompletesAfterAcknowledgment() {
        assertThat(service.replay(request).getResult()).isEqualTo("REPLAYED");
        var order = inOrder(transactions, kafka);
        order.verify(transactions).begin(request, "user-event", DlqMessageHandleAction.REPLAY, null);
        var captor = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        order.verify(kafka).send(captor.capture());
        order.verify(transactions).finish(request, 1L, DlqMessageHandleStatus.SUCCESS, null);
        ProducerRecord<byte[], byte[]> sent = captor.getValue();
        assertThat(sent.headers().lastHeader("trace").value()).containsExactly(1);
        assertThat(sent.headers().lastHeader("kafka_dlt-exception-message")).isNull();
        assertThat(sent.value()).containsExactly(2);
    }

    @Test
    void claimFailureNeverPublishes() {
        when(transactions.begin(any(), any(), any(), any())).thenThrow(new IllegalStateException("이미 처리 중"));
        assertThatThrownBy(() -> service.replay(request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(kafka);
    }

    @Test
    void timeoutRecordsUnknownAndDoesNotAutomaticallyRetry() {
        ReflectionTestUtils.setField(service, "sendTimeoutMs", 1L);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
        assertThatThrownBy(() -> service.replay(request)).hasMessageContaining("UNKNOWN");
        verify(transactions).finish(request, 1L, DlqMessageHandleStatus.UNKNOWN, "TimeoutException");
        verify(kafka, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void definitiveSerializationFailureRecordsFailed() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new SerializationException("bad")));
        assertThatThrownBy(() -> service.replay(request)).hasMessageContaining("FAILED");
        verify(transactions).finish(request, 1L, DlqMessageHandleStatus.FAILED, "SerializationException");
    }

    @Test
    void successRecordingFailureIsNotOverwrittenAsPublishFailure() {
        doThrow(new IllegalStateException("DB unavailable")).when(transactions).finish(request, 1L, DlqMessageHandleStatus.SUCCESS, null);
        assertThatThrownBy(() -> service.replay(request)).hasMessage("DB unavailable");
        verify(transactions, times(1)).finish(any(), anyLong(), any(), any());
    }

    @Test
    void failureRecordingFailureIsPreserved() {
        when(kafka.send(any(ProducerRecord.class))).thenThrow(new SerializationException("bad"));
        doThrow(new IllegalStateException("DB unavailable")).when(transactions).finish(any(), anyLong(), any(), any());
        assertThatThrownBy(() -> service.replay(request)).satisfies(e -> assertThat(e.getCause().getSuppressed()).hasSize(1));
    }

    @Test
    void modifiedPayloadIsValidatedBeforeClaim() {
        assertThatThrownBy(() -> service.replayModified(new DlqMessageModifiedReplayRequest(
                "user-event.DLT", 0, 10L, "admin", "복구", "not-json"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions, kafka);
    }

    @Test
    void modifiedReplayUsesDedicatedAction() {
        assertThat(service.replayModified(new DlqMessageModifiedReplayRequest(
                "user-event.DLT", 0, 10L, "admin", "복구", "{}" )).getResult()).isEqualTo("MODIFIED_REPLAYED");
        verify(transactions).begin(any(), eq("user-event"), eq(DlqMessageHandleAction.MODIFIED_REPLAY), eq("{}"));
    }

    @Test
    void discardDoesNotPublish() {
        assertThat(service.discard(request).getResult()).isEqualTo("DISCARDED");
        verifyNoInteractions(kafka);
    }

    @Test
    void forbiddenTopicDoesNotClaim() {
        assertThatThrownBy(() -> service.replay(new DlqMessageHandleRequest("other", 0, 1L, "a", "b")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions, kafka);
    }
}
