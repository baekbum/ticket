package dev.bum.common.kafka.dlt;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KafkaDltSlackNotifierTest {

    @Test
    @DisplayName("DLT 발생 정보를 Slack webhook으로 전송")
    void notifyDltSendsSlackMessage() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        KafkaDltSlackProperties properties = new KafkaDltSlackProperties();
        properties.setWebhookUrl("http://localhost/slack");
        properties.setAdminDlqUrl("http://localhost:8999/admin/api/v1/view/embed/kafkaDlq");
        KafkaDltSlackNotifier notifier = new KafkaDltSlackNotifier(properties, restClientBuilder);
        ReflectionTestUtils.setField(notifier, "serviceName", "auth-service");

        server.expect(requestTo("http://localhost/slack"))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("[DLT] Kafka 메시지 처리 실패")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("*Service:* auth-service")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("*Origin Topic:* user-event")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("*DLT Topic:* user-event.DLT")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("*DLQ 확인:* <http://localhost:8999/admin/api/v1/view/embed/kafkaDlq|관리자 DLQ 메뉴로 이동>")))
                .andRespond(withSuccess());

        boolean notified = notifier.notifyDlt(
                new ConsumerRecord<>("user-event", 0, 10L, "user01", "{\"userId\":\"user01\"}"),
                new IllegalStateException("consume failed"),
                new TopicPartition("user-event.DLT", 0)
        );

        assertThat(notified).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Slack webhook URL이 없으면 전송하지 않음")
    void notifyDltSkipsWhenWebhookUrlIsBlank() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        KafkaDltSlackNotifier notifier = new KafkaDltSlackNotifier(new KafkaDltSlackProperties(), restClientBuilder);

        boolean notified = notifier.notifyDlt(
                new ConsumerRecord<>("user-event", 0, 10L, "user01", "{\"userId\":\"user01\"}"),
                new IllegalStateException("consume failed"),
                new TopicPartition("user-event.DLT", 0)
        );

        assertThat(notified).isFalse();
        server.verify();
    }
}
