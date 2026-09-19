package dev.bum.admin_service.controller.notice;

import dev.bum.admin_service.feign.notice.NoticeServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.notice.dto.CreateNoticeRequest;
import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.common.service.support.notice.dto.NoticeSearchRequest;
import dev.bum.common.service.support.notice.dto.UpdateNoticeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notice")
public class AdminNoticeController {

    private final NoticeServiceClient noticeServiceClient;

    @PostMapping("/insert")
    public ResponseEntity<NoticeResponse> insert(@Valid @RequestBody CreateNoticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noticeServiceClient.insert(request));
    }

    @GetMapping("/select/id/{noticeId}")
    public ResponseEntity<NoticeResponse> selectById(@PathVariable Long noticeId) {
        return ResponseEntity.ok(noticeServiceClient.selectById(noticeId));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<NoticeResponse>> select(
            @Valid @ModelAttribute NoticeSearchRequest request
    ) {
        return ResponseEntity.ok(noticeServiceClient.select(request));
    }

    @PutMapping("/update/id/{noticeId}")
    public ResponseEntity<NoticeResponse> update(
            @PathVariable Long noticeId,
            @Valid @RequestBody UpdateNoticeRequest request
    ) {
        return ResponseEntity.ok(noticeServiceClient.update(noticeId, request));
    }

    @DeleteMapping("/delete/id/{noticeId}")
    public ResponseEntity<Void> delete(@PathVariable Long noticeId) {
        noticeServiceClient.delete(noticeId);
        return ResponseEntity.noContent().build();
    }
}
