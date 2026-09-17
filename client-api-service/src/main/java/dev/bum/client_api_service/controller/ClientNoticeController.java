package dev.bum.client_api_service.controller;

import dev.bum.client_api_service.feign.NoticeServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.enums.NoticeCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notice")
@RequiredArgsConstructor
public class ClientNoticeController {

    private final NoticeServiceClient noticeServiceClient;

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<NoticeResponse>> select(
            @RequestParam(required = false) NoticeCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(noticeServiceClient.select(category, keyword, page, size));
    }

    @GetMapping("/select/id/{noticeId}")
    public ResponseEntity<NoticeResponse> selectById(@PathVariable Long noticeId) {
        return ResponseEntity.ok(noticeServiceClient.selectById(noticeId));
    }
}
