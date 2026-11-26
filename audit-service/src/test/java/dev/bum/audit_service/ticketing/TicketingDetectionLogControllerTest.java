package dev.bum.audit_service.ticketing;

import dev.bum.audit_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.ticketing.TicketingDetectionType;
import dev.bum.common.security.TokenState;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.audit.dto.TicketingDetectionLogCondRequest;
import dev.bum.common.service.audit.dto.TicketingDetectionLogListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketingDetectionLogController.class)
@Import(SecurityConfig.class)
class TicketingDetectionLogControllerTest {

    private static final String URL = "/api/v1/ticketing-detection-log/select";

    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenStateStore tokenStateStore;
    @MockitoBean TicketingDetectionLogPersistenceService persistenceService;

    @Test
    void admin_can_filter_and_receive_the_list_page() throws Exception {
        authenticate("admin", "ROLE_ADMIN");
        Instant from = Instant.parse("2026-11-24T00:00:00Z");
        TicketingDetectionType type = TicketingDetectionType.PREPARE_TOO_FAST;
        TicketingDetectionLogCondRequest criteria = new TicketingDetectionLogCondRequest(101L, "buyer", type,
                from, null, 0, 20);
        given(persistenceService.selectByCond(criteria)).willReturn(CustomPageResponse.of(
                List.of(new TicketingDetectionLogListResponse(7L, 101L, "buyer", type, from)), 20, 0, 1, 1));

        mvc.perform(post(URL).header("Authorization", "Bearer admin").contentType(MediaType.APPLICATION_JSON).content("""
                        {"eventId":101,"userId":"buyer","detectionType":"PREPARE_TOO_FAST",
                         "occurredFrom":"2026-11-24T09:00:00+09:00","page":0,"size":20}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].eventId").value(101))
                .andExpect(jsonPath("$.content[0].userId").value("buyer"))
                .andExpect(jsonPath("$.content[0].detectionType").value(type.name()))
                .andExpect(jsonPath("$.content[0].occurredAt").value(from.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void anonymous_and_regular_users_cannot_query_detection_logs() throws Exception {
        authenticate("buyer", "ROLE_USER");

        mvc.perform(post(URL)).andExpect(status().isForbidden());
        mvc.perform(post(URL).header("Authorization", "Bearer buyer")).andExpect(status().isForbidden());

        verifyNoInteractions(persistenceService);
    }

    private void authenticate(String userId, String role) {
        given(jwtTokenProvider.validateToken(userId)).willReturn(true);
        given(jwtTokenProvider.getTokenType(userId)).willReturn("access");
        given(jwtTokenProvider.getUserId(userId)).willReturn(userId);
        given(jwtTokenProvider.getTokenVersion(userId)).willReturn(1L);
        given(jwtTokenProvider.getRole(userId)).willReturn(role);
        given(tokenStateStore.get(userId)).willReturn(new TokenState(1L, true, role));
        given(jwtTokenProvider.getAuthentication(userId)).willReturn(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
