package dev.bum.client_api_service.controller;

import dev.bum.client_api_service.feign.NoticeServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.enums.NoticeCategory;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientNoticeControllerTest {

    @Test
    void select_forwards_search_conditions_and_page() throws Exception {
        NoticeServiceClient client = mock(NoticeServiceClient.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientNoticeController(client)).build();
        when(client.select(NoticeCategory.SERVICE, "점검", 1, 20))
                .thenReturn(CustomPageResponse.of(List.<NoticeResponse>of(), 20, 1, 0, 0));

        mvc.perform(get("/api/v1/notice/select")
                        .param("category", "SERVICE")
                        .param("keyword", "점검")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(1));

        verify(client).select(NoticeCategory.SERVICE, "점검", 1, 20);
    }
}
