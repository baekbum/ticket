package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.common.security.TokenStateStore;
import dev.bum.ticket_service.controller.test.TicketingDetectionLogTestController;
import dev.bum.ticket_service.controller.test.TicketingDetectionLogTestController.TestRun;
import dev.bum.ticket_service.kafka.ticketing.TicketingDetectionLogProducer;
import dev.bum.ticket_service.security.InternalServiceTokenValidator;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.queue.BookingSessionKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketingDetectionLogTestController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("local")
class TicketingDetectionLogTestControllerTest {

    private static final String URL = "/api/v1/manage/test/ticketing-detection-log/publish";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenStateStore tokenStateStore;
    @MockitoBean InternalServiceTokenValidator internalServiceTokenValidator;
    @MockitoBean TicketingDetectionLogProducer producer;

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_can_publish_one_marked_sample_per_detection_type() throws Exception {
        TestRun run = publish("""
                {"userId":" test-buyer ","detectionType":null}
                """);

        assertThat(run.userId()).isEqualTo("test-buyer");
        assertThat(run.types()).containsExactly(TicketingDetectionType.values());
        ArgumentCaptor<TicketingDetectionType> type = ArgumentCaptor.forClass(TicketingDetectionType.class);
        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);
        verify(producer, times(run.types().size())).report(eq(0L), eq(run.userId()), nonce.capture(), type.capture(),
                any(Instant.class), argThat(evidence -> Boolean.TRUE.equals(evidence.get("test"))));
        assertThat(type.getAllValues()).containsExactlyElementsOf(run.types());
        assertThat(nonce.getAllValues()).allSatisfy(value ->
                assertThat(BookingSessionKey.hash(0L, run.userId(), value)).isEqualTo(run.sessionKeyHash()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void selecting_one_type_uses_a_new_session_for_each_run_with_the_same_user() throws Exception {
        String request = """
                {"userId":"test-buyer","detectionType":"PREPARE_TOO_FAST"}
                """;
        TestRun first = publish(request);
        TestRun second = publish(request);
        TicketingDetectionType type = TicketingDetectionType.PREPARE_TOO_FAST;

        assertThat(first.types()).containsExactly(type);
        assertThat(second.types()).containsExactly(type);
        assertThat(first.sessionKeyHash()).isNotEqualTo(second.sessionKeyHash());
        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);
        verify(producer, times(2)).report(eq(0L), eq("test-buyer"), nonce.capture(), eq(type),
                any(Instant.class), argThat(evidence -> Boolean.TRUE.equals(evidence.get("test"))));
        assertThat(nonce.getAllValues().stream().map(value -> BookingSessionKey.hash(0L, "test-buyer", value)))
                .containsExactly(first.sessionKeyHash(), second.sessionKeyHash());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blank_user_id_is_rejected() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(producer);
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_publish_samples() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"test-buyer\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(producer);
    }

    private TestRun publish(String request) throws Exception {
        return mapper.readValue(mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), TestRun.class);
    }
}
