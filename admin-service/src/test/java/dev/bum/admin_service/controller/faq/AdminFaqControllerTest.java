package dev.bum.admin_service.controller.faq;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.faq.FaqServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.support.faq.dto.CreateFaqRequest;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.dto.UpdateFaqRequest;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(AdminFaqController.class)
class AdminFaqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private FaqServiceClient faqServiceClient;

    @Test
    void 인증_정보가_없으면_FAQ_관리에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/faq/select"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void FAQ를_등록한다() throws Exception {
        CreateFaqRequest request = new CreateFaqRequest(
                "예매 내역은 어디에서 확인하나요?", "마이티켓에서 확인할 수 있습니다.",
                FaqCategory.BOOKING, 1, true
        );
        given(faqServiceClient.insert(any())).willReturn(response());

        mockMvc.perform(post("/api/v1/faq/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.faqId").value(1L));

        then(faqServiceClient).should().insert(request);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 조건으로_FAQ를_조회한다() throws Exception {
        given(faqServiceClient.select(any())).willReturn(
                CustomPageResponse.of(List.of(response()), 10, 0, 1, 1)
        );

        mockMvc.perform(get("/api/v1/faq/select")
                        .param("status", "PUBLISHED")
                        .param("category", "BOOKING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].question").value("예매 내역은 어디에서 확인하나요?"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void FAQ를_수정한다() throws Exception {
        UpdateFaqRequest request = new UpdateFaqRequest(
                "수정 질문", "수정 답변", FaqCategory.PAYMENT, PublicationStatus.PUBLISHED, 2
        );
        given(faqServiceClient.update(1L, request)).willReturn(response());

        mockMvc.perform(put("/api/v1/faq/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        then(faqServiceClient).should().update(1L, request);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void FAQ를_보관_처리한다() throws Exception {
        mockMvc.perform(delete("/api/v1/faq/delete/id/1"))
                .andExpect(status().isNoContent());

        then(faqServiceClient).should().delete(1L);
    }

    private FaqResponse response() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 12, 0);
        return new FaqResponse(
                1L,
                "예매 내역은 어디에서 확인하나요?",
                "마이티켓에서 확인할 수 있습니다.",
                FaqCategory.BOOKING,
                PublicationStatus.PUBLISHED,
                1,
                "admin",
                now,
                now
        );
    }
}
