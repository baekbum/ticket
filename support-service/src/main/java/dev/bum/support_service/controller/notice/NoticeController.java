package dev.bum.support_service.controller.notice;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.enums.NoticeCategory;
import dev.bum.support_service.service.notice.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notice")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<NoticeResponse>> select(
            @RequestParam(required = false) NoticeCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(noticeService.selectPublished(category, keyword, page, size));
    }

    @GetMapping("/select/id/{noticeId}")
    public ResponseEntity<NoticeResponse> selectById(@PathVariable Long noticeId) {
        return ResponseEntity.ok(noticeService.selectPublishedById(noticeId));
    }
}
