package dev.bum.admin_service.controller.monitoring;

import dev.bum.admin_service.monitoring.FailureMetricLevel;
import dev.bum.admin_service.monitoring.FailureMetricResponse;
import dev.bum.admin_service.monitoring.FailureMetricService;
import dev.bum.admin_service.monitoring.FailureMetricSummaryResponse;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminMonitoringController.class)
class AdminMonitoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private FailureMetricService failureMetricService;

    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @Test
    @DisplayName("핵심 장애 지표 조회")
    void failure_metrics() throws Exception {
        FailureMetricSummaryResponse response = new FailureMetricSummaryResponse(
                OffsetDateTime.parse("2026-08-09T10:00:00+09:00"),
                "10m",
                List.of(new FailureMetricResponse(
                        "http_5xx_rate",
                        "HTTP 5xx 비율",
                        "전체 HTTP 요청 중 5xx 응답 비율",
                        "%",
                        2.5,
                        1.0,
                        5.0,
                        FailureMetricLevel.WARNING,
                        "promql"
                ))
        );
        given(failureMetricService.failureMetrics("10m")).willReturn(response);

        mockMvc.perform(get("/api/v1/manage/monitoring/failure-metrics")
                        .param("range", "10m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("10m"))
                .andExpect(jsonPath("$.metrics[0].key").value("http_5xx_rate"))
                .andExpect(jsonPath("$.metrics[0].level").value("WARNING"));

        then(failureMetricService).should().failureMetrics("10m");
    }
}
