package dev.bum.support_service.controller.faq;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.CreateFaqRequest;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.dto.FaqSearchRequest;
import dev.bum.common.service.support.faq.dto.UpdateFaqRequest;
import dev.bum.support_service.service.faq.FaqManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/manage/faq")
public class FaqManagementController {

    private final FaqManagementService faqManagementService;

    @PostMapping("/insert")
    public ResponseEntity<FaqResponse> insert(
            @Valid @RequestBody CreateFaqRequest request,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(faqManagementService.insert(request, currentUserId));
    }

    @GetMapping("/select/id/{faqId}")
    public ResponseEntity<FaqResponse> selectById(@PathVariable Long faqId) {
        return ResponseEntity.ok(faqManagementService.selectById(faqId));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<FaqResponse>> select(
            @Valid @ModelAttribute FaqSearchRequest request
    ) {
        return ResponseEntity.ok(faqManagementService.select(request));
    }

    @PutMapping("/update/id/{faqId}")
    public ResponseEntity<FaqResponse> update(
            @PathVariable Long faqId,
            @Valid @RequestBody UpdateFaqRequest request
    ) {
        return ResponseEntity.ok(faqManagementService.update(faqId, request));
    }

    @DeleteMapping("/delete/id/{faqId}")
    public ResponseEntity<Void> delete(@PathVariable Long faqId) {
        faqManagementService.delete(faqId);
        return ResponseEntity.noContent().build();
    }
}
