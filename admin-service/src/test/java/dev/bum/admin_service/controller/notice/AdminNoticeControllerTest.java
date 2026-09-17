package dev.bum.admin_service.controller.notice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.admin_service.feign.notice.NoticeServiceClient;
import dev.bum.admin_service.security.SecurityConfig;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.JwtAuthenticationFilter;
import dev.bum.common.service.support.notice.dto.CreateNoticeRequest;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.dto.UpdateNoticeRequest;
import dev.bum.common.service.support.notice.enums.NoticeCategory;
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
@WebMvcTest(AdminNoticeController.class)
class AdminNoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private NoticeServiceClient noticeServiceClient;

    @Test
    void 인증_정보가_없으면_공지사항_관리에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/notice/select"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 공지사항을_등록한다() throws Exception {
        CreateNoticeRequest request = new CreateNoticeRequest(
                "점검 안내", "점검 내용", NoticeCategory.MAINTENANCE, true, true
        );
        given(noticeServiceClient.insert(any())).willReturn(response());

        mockMvc.perform(post("/api/v1/notice/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noticeId").value(1L));

        then(noticeServiceClient).should().insert(request);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 조건으로_공지사항을_조회한다() throws Exception {
        given(noticeServiceClient.select(any())).willReturn(
                CustomPageResponse.of(List.of(response()), 10, 0, 1, 1)
        );

        mockMvc.perform(get("/api/v1/notice/select")
                        .param("status", "PUBLISHED")
                        .param("category", "MAINTENANCE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("점검 안내"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 공지사항을_수정한다() throws Exception {
        UpdateNoticeRequest request = new UpdateNoticeRequest(
                "수정 안내", "수정 내용", NoticeCategory.SERVICE, PublicationStatus.PUBLISHED, false
        );
        given(noticeServiceClient.update(1L, request)).willReturn(response());

        mockMvc.perform(put("/api/v1/notice/update/id/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        then(noticeServiceClient).should().update(1L, request);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void 공지사항을_보관_처리한다() throws Exception {
        mockMvc.perform(delete("/api/v1/notice/delete/id/1"))
                .andExpect(status().isNoContent());

        then(noticeServiceClient).should().delete(1L);
    }

    private NoticeResponse response() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 17, 12, 0);
        return new NoticeResponse(
                1L,
                "점검 안내",
                "점검 내용",
                NoticeCategory.MAINTENANCE,
                PublicationStatus.PUBLISHED,
                "admin",
                true,
                10,
                now,
                now,
                now
        );
    }
}
