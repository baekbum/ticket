package dev.bum.support_service.controller.inquiry;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.inquiry.dto.InquiryAnswerRequest;
import dev.bum.common.service.support.inquiry.dto.InquiryResponse;
import dev.bum.common.service.support.inquiry.dto.InquirySummaryResponse;
import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;
import dev.bum.support_service.service.inquiry.InquiryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manage/inquiry")
public class InquiryManagementController {

    private final InquiryManagementService inquiryManagementService;

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<InquirySummaryResponse>> select(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) InquiryCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(inquiryManagementService.select(status, category, keyword, page, size));
    }

    @GetMapping("/select/id/{inquiryId}")
    public ResponseEntity<InquiryResponse> selectById(@PathVariable Long inquiryId) {
        return ResponseEntity.ok(inquiryManagementService.selectById(inquiryId));
    }

    @PostMapping("/answer/id/{inquiryId}")
    public ResponseEntity<InquiryResponse> answer(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inquiryManagementService.answer(inquiryId, request, currentUserId));
    }

    @PutMapping("/answer/id/{inquiryId}")
    public ResponseEntity<InquiryResponse> updateAnswer(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request
    ) {
        return ResponseEntity.ok(inquiryManagementService.updateAnswer(inquiryId, request));
    }
}
