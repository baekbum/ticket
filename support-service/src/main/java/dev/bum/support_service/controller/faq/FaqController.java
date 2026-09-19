package dev.bum.support_service.controller.faq;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.support_service.service.faq.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/faq")
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<FaqResponse>> select(
            @RequestParam(required = false) FaqCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(faqService.selectPublished(category, keyword, page, size));
    }
}
