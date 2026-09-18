package dev.bum.client_api_service.controller;

import dev.bum.client_api_service.feign.FaqServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.enums.FaqCategory;
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

class ClientFaqControllerTest {

    @Test
    void select_forwards_category_keyword_and_page() throws Exception {
        FaqServiceClient client = mock(FaqServiceClient.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientFaqController(client)).build();
        when(client.select(FaqCategory.BOOKING, "좌석", 0, 100))
                .thenReturn(CustomPageResponse.of(List.<FaqResponse>of(), 100, 0, 0, 0));

        mvc.perform(get("/api/v1/faq/select")
                        .param("category", "BOOKING")
                        .param("keyword", "좌석"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(100));

        verify(client).select(FaqCategory.BOOKING, "좌석", 0, 100);
    }
}
