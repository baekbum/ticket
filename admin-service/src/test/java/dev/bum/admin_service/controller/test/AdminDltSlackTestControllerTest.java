package dev.bum.admin_service.controller.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import dev.bum.common.kafka.dlt.KafkaDltSlackProperties;
import dev.bum.common.security.JwtAuthenticationFilter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("local")
@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminDltSlackTestController.class)
class AdminDltSlackTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private KafkaDltSlackNotifier kafkaDltSlackNotifier;

    @MockitoBean
    private KafkaDltSlackProperties kafkaDltSlackProperties;

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("로컬 DLT Slack 테스트 설정 조회")
    void config() throws Exception {
        given(kafkaDltSlackProperties.isEnabled()).willReturn(true);
        given(kafkaDltSlackProperties.getWebhookUrl()).willReturn("https://hooks.slack.test");
        given(kafkaDltSlackProperties.getAdminDlqUrl()).willReturn("http://admin.test/admin/api/v1/view/embed/kafkaDlq");

        mockMvc.perform(get("/api/v1/manage/test/dlt/slack/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.webhookConfigured").value(true))
                .andExpect(jsonPath("$.adminDlqUrl").value("http://admin.test/admin/api/v1/view/embed/kafkaDlq"));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("로컬 DLT Slack 테스트 알림 전송")
    void send() throws Exception {
        given(kafkaDltSlackProperties.isEnabled()).willReturn(true);
        given(kafkaDltSlackProperties.getWebhookUrl()).willReturn("https://hooks.slack.test");
        given(kafkaDltSlackProperties.getAdminDlqUrl()).willReturn("http://admin.test/admin/api/v1/view/embed/kafkaDlq");
        given(kafkaDltSlackNotifier.notifyDlt(anyConsumerRecord(), any(Exception.class), any(TopicPartition.class)))
                .willReturn(true);

        DltSlackTestRequest request = new DltSlackTestRequest(
                "user-event",
                "user-event.DLT",
                0,
                0,
                10L,
                "admin-dlt-slack-test",
                "{\"test\":true}",
                "관리자 DLT Slack 테스트"
        );

        mockMvc.perform(post("/api/v1/manage/test/dlt/slack/send")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.originTopic").value("user-event"))
                .andExpect(jsonPath("$.dltTopic").value("user-event.DLT"))
                .andExpect(jsonPath("$.adminDlqUrl").value("http://admin.test/admin/api/v1/view/embed/kafkaDlq"));

        then(kafkaDltSlackNotifier).should()
                .notifyDlt(anyConsumerRecord(), any(Exception.class), any(TopicPartition.class));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("Slack webhook이 없으면 테스트 알림을 생략")
    void sendSkipsWhenWebhookUrlIsBlank() throws Exception {
        given(kafkaDltSlackProperties.isEnabled()).willReturn(true);
        given(kafkaDltSlackProperties.getWebhookUrl()).willReturn("");

        DltSlackTestRequest request = new DltSlackTestRequest(
                "user-event",
                "user-event.DLT",
                0,
                0,
                10L,
                "admin-dlt-slack-test",
                "{\"test\":true}",
                "관리자 DLT Slack 테스트"
        );

        mockMvc.perform(post("/api/v1/manage/test/dlt/slack/send")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.skippedReason").value("Slack webhook URL이 설정되어 있지 않습니다."));

        then(kafkaDltSlackNotifier).shouldHaveNoInteractions();
    }

    private ConsumerRecord<?, ?> anyConsumerRecord() {
        return any();
    }
}
