package dev.bum.client_api_service.feign;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.enums.NoticeCategory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "client-support-notice-service",
        url = "${services.support-service.url}",
        path = "/api/v1/notice"
)
public interface NoticeServiceClient {

    @GetMapping("/select")
    CustomPageResponse<NoticeResponse> select(
            @RequestParam(value = "category", required = false) NoticeCategory category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    @GetMapping("/select/id/{noticeId}")
    NoticeResponse selectById(@PathVariable("noticeId") Long noticeId);
}
