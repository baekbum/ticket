package dev.bum.admin_service.controller.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.config.KafkaDlqProperties;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
@WebMvcTest(AdminDltTestController.class)
class AdminDltTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private KafkaTemplate<byte[], byte[]> kafkaTemplate;

    @MockitoBean
    private KafkaDlqProperties kafkaDlqProperties;

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("로컬 DLT 테스트 topic 목록 조회")
    void topics() throws Exception {
        given(kafkaDlqProperties.getMappings()).willReturn(Map.of(
                "user-event.DLT", "user-event",
                "audit-log.DLT", "audit-log"
        ));

        mockMvc.perform(get("/api/v1/manage/test/dlt/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("audit-log.DLT"))
                .andExpect(jsonPath("$[1]").value("user-event.DLT"));
    }

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("로컬 DLT 테스트 메시지 발행")
    void publish() throws Exception {
        given(kafkaDlqProperties.getMappings()).willReturn(Map.of("user-event.DLT", "user-event"));
        given(kafkaTemplate.send(anyDltRecord()))
                .willReturn(CompletableFuture.completedFuture(sendResult()));

        DltTestPublishRequest request = new DltTestPublishRequest(
                "user-event.DLT",
                "admin-dlt-test",
                "{\"test\":true}"
        );

        mockMvc.perform(post("/api/v1/manage/test/dlt/publish")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dltTopic").value("user-event.DLT"))
                .andExpect(jsonPath("$.partition").value(0))
                .andExpect(jsonPath("$.offset").value(10))
                .andExpect(jsonPath("$.key").value("admin-dlt-test"));

        then(kafkaTemplate).should().send(anyDltRecord());
    }

    private ProducerRecord<byte[], byte[]> anyDltRecord() {
        return any();
    }

    private SendResult<byte[], byte[]> sendResult() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("user-event.DLT", 0),
                10L,
                0,
                System.currentTimeMillis(),
                0,
                0
        );
        return new SendResult<>(null, metadata);
    }
}
