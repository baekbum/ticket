package dev.bum.admin_service.feign.notice;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.CreateNoticeRequest;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.dto.NoticeSearchRequest;
import dev.bum.common.service.support.notice.dto.UpdateNoticeRequest;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "support-service",
        url = "${services.support-service.url}",
        path = "/api/v1/manage/notice"
)
public interface NoticeServiceClient {

    @PostMapping("/insert")
    NoticeResponse insert(@Valid @RequestBody CreateNoticeRequest request);

    @GetMapping("/select/id/{noticeId}")
    NoticeResponse selectById(@PathVariable("noticeId") Long noticeId);

    @GetMapping("/select")
    CustomPageResponse<NoticeResponse> select(@SpringQueryMap NoticeSearchRequest request);

    @PutMapping("/update/id/{noticeId}")
    NoticeResponse update(
            @PathVariable("noticeId") Long noticeId,
            @Valid @RequestBody UpdateNoticeRequest request
    );

    @DeleteMapping("/delete/id/{noticeId}")
    void delete(@PathVariable("noticeId") Long noticeId);
}
