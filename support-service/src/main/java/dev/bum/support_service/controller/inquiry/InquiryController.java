package dev.bum.support_service.controller.inquiry;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.inquiry.dto.CreateInquiryRequest;
import dev.bum.common.service.support.inquiry.dto.InquiryResponse;
import dev.bum.common.service.support.inquiry.dto.InquirySummaryResponse;
import dev.bum.common.service.support.inquiry.dto.UpdateInquiryRequest;
import dev.bum.support_service.service.inquiry.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inquiry")
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping("/insert")
    public ResponseEntity<InquiryResponse> insert(
            @Valid @RequestBody CreateInquiryRequest request,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inquiryService.insert(request, currentUserId));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<InquirySummaryResponse>> selectMine(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(inquiryService.selectMine(currentUserId, page, size));
    }

    @GetMapping("/select/id/{inquiryId}")
    public ResponseEntity<InquiryResponse> selectById(
            @PathVariable Long inquiryId,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.ok(inquiryService.selectById(inquiryId, currentUserId));
    }

    @PutMapping("/update/id/{inquiryId}")
    public ResponseEntity<InquiryResponse> update(
            @PathVariable Long inquiryId,
            @Valid @RequestBody UpdateInquiryRequest request,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.ok(inquiryService.update(inquiryId, request, currentUserId));
    }

    @DeleteMapping("/delete/id/{inquiryId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long inquiryId,
            @AuthenticationPrincipal String currentUserId
    ) {
        inquiryService.delete(inquiryId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
